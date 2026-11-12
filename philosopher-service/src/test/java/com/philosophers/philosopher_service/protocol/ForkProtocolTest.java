package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test du protocole à jetons (partie 3.3) avec un réseau simulé en mémoire :
 * les messages sont retardés au hasard (donc reçus dans le désordre) et,
 * dans le second test, une partie des demandes est perdue.
 */
class ForkProtocolTest {

    /** Réseau simulé reliant N protocoles entre eux. */
    static class FakeNetwork implements PeerSender {
        final ForkProtocol[] nodes;
        final Random random = new Random();
        final double requestLossRate;

        FakeNetwork(int n, double requestLossRate) {
            this.nodes = new ForkProtocol[n];
            this.requestLossRate = requestLossRate;
        }

        private void randomDelay() {
            try {
                Thread.sleep(random.nextInt(4));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void sendRequest(int peerId, RequestMessage m) {
            if (random.nextDouble() < requestLossRate) {
                return;   // message perdu : sera rediffusé par retransmission
            }
            randomDelay();
            nodes[peerId].onRequest(m);
        }

        @Override
        public void sendFinished(int peerId, FinishedMessage m) {
            randomDelay();
            nodes[peerId].onFinished(m);
        }

        @Override
        public boolean sendToken(int peerId, TokenMessage m) {
            randomDelay();
            nodes[peerId].onToken(m);
            return true;
        }
    }

    /** Lance N philosophes qui mangent plusieurs fois et vérifie les propriétés du protocole. */
    private void runScenario(int n, int mealsEach, double requestLossRate, long retransmitMs) throws Exception {
        FakeNetwork net = new FakeNetwork(n, requestLossRate);
        ExecutorService netPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < n; i++) {
            net.nodes[i] = new ForkProtocol(i, n, net, netPool, retransmitMs);
        }

        AtomicInteger[] usersOfFork = new AtomicInteger[n];
        for (int i = 0; i < n; i++) {
            usersOfFork[i] = new AtomicInteger();
        }
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger[] meals = new AtomicInteger[n];
        for (int i = 0; i < n; i++) {
            meals[i] = new AtomicInteger();
        }

        ExecutorService philosophers = Executors.newFixedThreadPool(n);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int p = i;
            philosophers.submit(() -> {
                int left = p;
                int right = (p + 1) % n;
                try {
                    for (int m = 0; m < mealsEach; m++) {
                        net.nodes[p].acquireForks();
                        // Section critique : chaque fourchette ne doit avoir qu'un utilisateur
                        if (usersOfFork[left].incrementAndGet() > 1) violations.incrementAndGet();
                        if (usersOfFork[right].incrementAndGet() > 1) violations.incrementAndGet();
                        Thread.sleep(2);
                        meals[p].incrementAndGet();
                        usersOfFork[right].decrementAndGet();
                        usersOfFork[left].decrementAndGet();
                        net.nodes[p].releaseForks();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        boolean finished = done.await(60, TimeUnit.SECONDS);
        philosophers.shutdownNow();

        assertTrue(finished, "tous les philosophes doivent finir (ni interblocage ni famine)");
        assertEquals(0, violations.get(), "une fourchette a été utilisée par deux philosophes à la fois");
        for (int i = 0; i < n; i++) {
            assertEquals(mealsEach, meals[i].get(), "le philosophe " + i + " doit avoir mangé " + mealsEach + " fois");
        }
    }

    @Test
    void fivePhilosophers_exclusionEquityAndNoDeadlock() throws Exception {
        runScenario(5, 30, 0.0, 3000);
    }

    @Test
    void threePhilosophers_exclusionEquityAndNoDeadlock() throws Exception {
        runScenario(3, 30, 0.0, 3000);
    }

    @Test
    void lostRequests_areRecoveredByRetransmission() throws Exception {
        runScenario(5, 15, 0.3, 100);
    }
}
