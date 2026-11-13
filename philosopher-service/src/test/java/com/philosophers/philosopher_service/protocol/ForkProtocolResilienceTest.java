package com.philosophers.philosopher_service.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de résilience : délais de réponse, perte de messages, indisponibilité d'un
 * philosophe et interruption (partition) du réseau. Dans tous les cas la sûreté
 * (exclusion mutuelle) doit rester vraie ; la vivacité doit revenir quand la panne cesse.
 */
class ForkProtocolResilienceTest {

    @AfterEach
    void shutdownClusters() {
        SimulatedCluster.closeAll();
    }

    @Test
    void slowNetwork_protocolStillCorrect() throws Exception {
        SimulatedCluster c = new SimulatedCluster(5, 3000);
        c.maxDelayMs = 80;   // chaque message prend jusqu'à 80 ms
        assertTrue(c.runAll(SimulatedCluster.all(5), 8, 2, 90));
        assertEquals(0, c.violations.get());
    }

    @Test
    void lostRequests_recoveredByRetransmission() throws Exception {
        SimulatedCluster c = new SimulatedCluster(5, 100);
        c.requestLossRate = 0.4;
        assertTrue(c.runAll(SimulatedCluster.all(5), 10, 1, 90));
        assertEquals(0, c.violations.get());
    }

    @Test
    void lostFinishedMessages_doNotBreakSafety() throws Exception {
        // FINISHED perdus : des demandes périmées restent mémorisées chez les autres.
        // Cela coûte des transferts inutiles mais ne doit pas casser l'exclusion ni bloquer.
        SimulatedCluster c = new SimulatedCluster(5, 100);
        c.finishedLossRate = 0.3;
        assertTrue(c.runAll(SimulatedCluster.all(5), 8, 1, 90));
        assertEquals(0, c.violations.get());
    }

    @Test
    void unavailablePhilosopher_othersNotUsingItsForkKeepWorking() throws Exception {
        // N = 5, P4 hors service : il garde F4. Les philosophes 0,1,2 n'ont pas besoin de F4
        // (P0: F0,F1 ; P1: F1,F2 ; P2: F2,F3) et doivent continuer à manger.
        SimulatedCluster c = new SimulatedCluster(5, 100);
        c.down[4] = true;
        assertTrue(c.runAll(new int[]{0, 1, 2}, 10, 1, 60),
                "les philosophes indépendants de P4 doivent progresser");
        assertEquals(0, c.violations.get());
    }

    @Test
    void unavailablePhilosopher_blocksOnlyItsNeighbours_thenRecovers() throws Exception {
        SimulatedCluster c = new SimulatedCluster(5, 100);
        c.down[4] = true;   // F4 inaccessible : P3 (F3,F4) et P4 (F4,F0) ne peuvent pas manger

        AtomicBoolean p3Done = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        Thread p3 = new Thread(() -> {
            try {
                c.eatOnce(3, 1);
                p3Done.set(true);
            } catch (InterruptedException ignored) {
            } finally {
                finished.countDown();
            }
        });
        p3.setDaemon(true);
        p3.start();

        assertFalse(finished.await(1500, TimeUnit.MILLISECONDS), "P3 ne peut pas manger sans F4");

        c.down[4] = false;   // P4 revient : les retransmissions aboutissent
        assertTrue(finished.await(20, TimeUnit.SECONDS), "P3 doit manger après le retour de P4");
        assertTrue(p3Done.get());
        assertEquals(0, c.violations.get());
    }

    @Test
    void networkPartition_safetyHeld_progressResumesAfterHeal() throws Exception {
        SimulatedCluster c = new SimulatedCluster(5, 100);
        c.partitioned = true;

        Thread healer = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            c.partitioned = false;
        });
        healer.setDaemon(true);
        healer.start();

        assertTrue(c.runAll(SimulatedCluster.all(5), 5, 1, 90), "le système doit se rétablir après la partition");
        assertEquals(0, c.violations.get(), "aucune violation pendant la partition");
    }

    @Test
    void crashedRecipient_tokenIsNotLost() throws Exception {
        // P0 cède F0 à P4 alors que P4 est injoignable : le jeton doit revenir chez P0.
        SimulatedCluster c = new SimulatedCluster(5, 60_000);
        c.down[4] = true;
        c.nodes[0].onRequest(new com.philosophers.philosopher_service.dto.RequestMessage(4, 1));

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && !c.nodes[0].holdsToken(0)) {
            Thread.sleep(50);
        }
        // le jeton oscille entre "en transit" et "repris" ; on vérifie qu'il n'est jamais perdu
        c.down[4] = false;
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end && !c.nodes[4].holdsToken(0)) {
            Thread.sleep(50);
        }
        assertTrue(c.nodes[4].holdsToken(0), "le jeton F0 doit finir chez P4 une fois joignable");
        assertFalse(c.nodes[0].holdsToken(0), "et ne plus être chez P0 (jeton unique)");
    }

    @Test
    void ghostRequests_doNotCauseEndlessTokenBouncing() throws Exception {
        // Deux demandes périmées (leur FINISHED a été perdu, ou le demandeur est tombé) sont
        // mémorisées chacune par l'autre philosophe : sans péremption, F2 rebondirait à l'infini.
        SimulatedCluster c = new SimulatedCluster(5, 50);   // péremption après 200 ms
        c.maxDelayMs = 0;
        c.nodes[2].onRequest(new com.philosophers.philosopher_service.dto.RequestMessage(1, 118));
        c.nodes[1].onRequest(new com.philosophers.philosopher_service.dto.RequestMessage(2, 125));

        Thread.sleep(1500);   // laisse le temps aux demandes fantômes d'expirer
        int before = c.tokensSent.get();
        Thread.sleep(500);
        assertEquals(before, c.tokensSent.get(), "les jetons rebondissent encore (livelock)");
        // (la péremption est paresseuse : une demande fantôme peut rester mémorisée,
        //  inerte, jusqu'au prochain message reçu par ce philosophe)
    }
}
