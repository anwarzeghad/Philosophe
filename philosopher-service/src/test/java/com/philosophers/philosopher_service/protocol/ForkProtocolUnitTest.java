package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires (JUnit 5 + Mockito) d'UN philosophe : le réseau (PeerSender) est un mock,
 * les messages sont envoyés de façon synchrone (executor = Runnable::run) pour rendre les
 * tests déterministes.
 *
 * Configuration : N = 5, philosophe 2 → fourchettes F2 (jeton détenu au départ) et F3.
 * Les voisins "demandeurs" : P1 a besoin de F1,F2 ; P3 a besoin de F3,F4 ; P0 de F0,F1.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForkProtocolUnitTest {

    private static final int N = 5;
    private static final int ME = 2;

    @Mock
    PeerSender sender;

    ForkProtocol protocol;
    ExecutorService background;

    @BeforeEach
    void setUp() {
        when(sender.sendToken(anyInt(), any())).thenReturn(true);
        protocol = new ForkProtocol(ME, N, sender, Runnable::run, 60_000);
        background = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        background.shutdownNow();
    }

    /** Lance acquireForks() dans un thread et attend que la demande soit enregistrée. */
    private Thread startAcquire() throws Exception {
        Thread t = new Thread(() -> {
            try {
                protocol.acquireForks();
            } catch (InterruptedException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        verify(sender, timeout(2000).times(N - 1)).sendRequest(anyInt(), any());
        return t;
    }

    // ------------------------------------------------------------- horloge logique

    @Test
    void nextClock_isStrictlyIncreasing() {
        long a = protocol.nextClock();
        long b = protocol.nextClock();
        assertEquals(a + 1, b);
    }

    @Test
    void receivingRequest_advancesLamportClock() {
        protocol.onRequest(new RequestMessage(0, 100));
        assertTrue(protocol.nextClock() > 100, "l'horloge doit dépasser l'horodatage reçu");
    }

    @Test
    void receivingToken_advancesLamportClock() {
        protocol.onToken(new TokenMessage(3, 3, 50));
        assertTrue(protocol.nextClock() > 50);
    }

    // ------------------------------------------------------------- traitement des demandes

    @Test
    void requestNeedingMyFork_makesMeGiveTheToken() {
        protocol.onRequest(new RequestMessage(1, 5));   // P1 a besoin de F1 et F2
        ArgumentCaptor<TokenMessage> token = ArgumentCaptor.forClass(TokenMessage.class);
        verify(sender).sendToken(eq(1), token.capture());
        assertEquals(2, token.getValue().fork());
        assertEquals(ME, token.getValue().fromId());
        assertFalse(protocol.holdsToken(2), "le jeton a été cédé");
    }

    @Test
    void requestNotNeedingMyFork_isOnlyRemembered() {
        protocol.onRequest(new RequestMessage(0, 5));   // P0 : F0 et F1, pas F2
        verify(sender, never()).sendToken(anyInt(), any());
        assertTrue(protocol.holdsToken(2));
        assertEquals(1, protocol.pendingCount(), "la demande est mémorisée");
    }

    @Test
    void duplicateRequest_isIgnored() {
        protocol.onRequest(new RequestMessage(1, 5));
        protocol.onRequest(new RequestMessage(1, 5));   // retransmission
        verify(sender, times(1)).sendToken(anyInt(), any());
        assertEquals(1, protocol.pendingCount());
    }

    @Test
    void requestArrivingAfterItsFinished_isNotAdmitted() {
        protocol.onFinished(new FinishedMessage(1, 5));   // messages reçus dans le désordre
        protocol.onRequest(new RequestMessage(1, 5));
        verify(sender, never()).sendToken(anyInt(), any());
        assertEquals(0, protocol.pendingCount());
    }

    @Test
    void finished_removesPendingRequest() {
        protocol.onRequest(new RequestMessage(0, 5));
        assertEquals(1, protocol.pendingCount());
        protocol.onFinished(new FinishedMessage(0, 5));
        assertEquals(0, protocol.pendingCount());
    }

    @Test
    void invalidMessages_areIgnored() {
        protocol.onRequest(new RequestMessage(99, 1));
        protocol.onRequest(new RequestMessage(-1, 1));
        protocol.onFinished(new FinishedMessage(42, 1));
        protocol.onToken(new TokenMessage(77, 0, 1));
        protocol.onToken(new TokenMessage(-3, 0, 1));
        assertEquals(0, protocol.pendingCount());
        verify(sender, never()).sendToken(anyInt(), any());
    }

    // ------------------------------------------------------------- gestion des jetons / priorité

    @Test
    void hungryPhilosopher_givesTokenToOlderRequest() throws Exception {
        startAcquire();                                // ma demande : ts = 1
        protocol.onRequest(new RequestMessage(1, 1));   // même ts, id plus petit → plus prioritaire
        verify(sender, timeout(2000)).sendToken(eq(1), any());
        assertFalse(protocol.holdsToken(2));
    }

    @Test
    void hungryPhilosopher_keepsTokenAgainstYoungerRequest() throws Exception {
        startAcquire();
        protocol.onRequest(new RequestMessage(1, 100));   // plus récente que la mienne
        verify(sender, never()).sendToken(anyInt(), any());
        assertTrue(protocol.holdsToken(2));
    }

    @Test
    void obtainingBothTokens_unblocksAcquire() throws Exception {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        Thread t = new Thread(() -> {
            try {
                protocol.acquireForks();
                done.set(true);
            } catch (InterruptedException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        verify(sender, timeout(2000).times(N - 1)).sendRequest(anyInt(), any());
        assertFalse(done.get(), "il manque encore le jeton F3");

        protocol.onToken(new TokenMessage(3, 3, 10));
        t.join(2000);
        assertTrue(done.get());
        assertTrue(protocol.isEating());
    }

    @Test
    void whileEating_noToken_isGivenUp_untilRelease() throws Exception {
        Thread t = startAcquire();
        protocol.onToken(new TokenMessage(3, 3, 10));
        t.join(2000);
        assertTrue(protocol.isEating());

        protocol.onRequest(new RequestMessage(1, 500));   // veut F2 pendant que je mange
        verify(sender, never()).sendToken(anyInt(), any());

        protocol.releaseForks();
        verify(sender).sendToken(eq(1), any());            // libération → jeton transmis
        assertFalse(protocol.isEating());
    }

    @Test
    void release_broadcastsFinishedToAllOthers() throws Exception {
        Thread t = startAcquire();
        protocol.onToken(new TokenMessage(3, 3, 10));
        t.join(2000);
        protocol.releaseForks();
        for (int p = 0; p < N; p++) {
            if (p != ME) {
                verify(sender).sendFinished(eq(p), any());
            }
        }
        verify(sender, never()).sendFinished(eq(ME), any());
    }

    @Test
    void acquire_broadcastsTimestampedRequestToEveryoneElse() throws Exception {
        startAcquire();
        ArgumentCaptor<RequestMessage> msg = ArgumentCaptor.forClass(RequestMessage.class);
        for (int p = 0; p < N; p++) {
            if (p != ME) {
                verify(sender).sendRequest(eq(p), msg.capture());
            }
        }
        assertTrue(msg.getAllValues().stream().allMatch(m -> m.requesterId() == ME && m.timestamp() > 0));
        verify(sender, never()).sendRequest(eq(ME), any());
    }

    // ------------------------------------------------------------- erreurs

    @Test
    void releaseWithoutAcquire_isHarmless() {
        assertDoesNotThrow(() -> protocol.releaseForks());
        verifyNoInteractions(sender);
    }

    @Test
    void unreachablePeer_tokenIsTakenBackThenRetried() {
        // 1er envoi échoue (pair injoignable), le 2e réussit
        when(sender.sendToken(anyInt(), any())).thenReturn(false, true);
        ForkProtocol p = new ForkProtocol(ME, N, sender, background, 60_000);
        p.onRequest(new RequestMessage(1, 5));
        verify(sender, timeout(5000).times(2)).sendToken(eq(1), any());
        // après le succès, le jeton n'est plus chez moi
        verify(sender, after(300).times(2)).sendToken(eq(1), any());
        assertFalse(p.holdsToken(2));
    }

    @Test
    void unansweredRequest_isRetransmitted() throws Exception {
        ForkProtocol p = new ForkProtocol(ME, N, sender, background, 50);
        Thread t = new Thread(() -> {
            try {
                p.acquireForks();
            } catch (InterruptedException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        verify(sender, timeout(3000).atLeast(3)).sendRequest(eq(0), any());
        t.interrupt();
    }

    @Test
    void acquire_canBeInterrupted() throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                protocol.acquireForks();
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        t.setDaemon(true);
        t.start();
        verify(sender, timeout(2000).times(N - 1)).sendRequest(anyInt(), any());
        t.interrupt();
        t.join(2000);
        assertInstanceOf(InterruptedException.class, thrown.get());
    }
}
