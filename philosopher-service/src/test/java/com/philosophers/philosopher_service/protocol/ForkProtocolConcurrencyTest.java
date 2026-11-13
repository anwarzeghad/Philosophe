package com.philosophers.philosopher_service.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de concurrence : tous les philosophes ont faim en même temps, plusieurs fois de suite.
 * On vérifie qu'une fourchette n'est jamais utilisée par deux philosophes simultanément,
 * qu'il n'y a ni interblocage ni famine, et que le nombre de mangeurs simultanés respecte
 * le maximum théorique floor(N/2).
 */
class ForkProtocolConcurrencyTest {

    @AfterEach
    void shutdownClusters() {
        SimulatedCluster.closeAll();
    }

    @RepeatedTest(5)
    void allHungryAtOnce_noForkSharedAndEveryoneEats() throws Exception {
        SimulatedCluster c = new SimulatedCluster(5, 3000);
        assertTrue(c.runAll(SimulatedCluster.all(5), 40, 1, 60), "interblocage ou famine");
        assertEquals(0, c.violations.get(), "fourchette utilisée par deux philosophes");
        for (int i = 0; i < 5; i++) {
            assertEquals(40, c.meals[i].get(), "philosophe " + i + " affamé");
        }
        assertTrue(c.maxConcurrentEaters.get() <= 2, "au plus 2 mangeurs avec 5 fourchettes");
    }

    @Test
    void largeRing_eightPhilosophers() throws Exception {
        SimulatedCluster c = new SimulatedCluster(8, 3000);
        assertTrue(c.runAll(SimulatedCluster.all(8), 25, 1, 90));
        assertEquals(0, c.violations.get());
        assertTrue(c.maxConcurrentEaters.get() <= 4);
    }

    @Test
    void minimalRing_twoPhilosophers_shareBothForks() throws Exception {
        // N = 2 : P0 utilise F0,F1 et P1 utilise F1,F0 → exclusion totale
        SimulatedCluster c = new SimulatedCluster(2, 3000);
        assertTrue(c.runAll(SimulatedCluster.all(2), 50, 1, 60));
        assertEquals(0, c.violations.get());
        assertEquals(1, c.maxConcurrentEaters.get(), "avec 2 philosophes, jamais deux mangeurs");
    }

    @Test
    void fairness_noPhilosopherIsStarved() throws Exception {
        // Tous mangent en boucle pendant une durée fixée : aucun ne doit être très en retard.
        int n = 5;
        SimulatedCluster c = new SimulatedCluster(n, 3000);
        long end = System.currentTimeMillis() + 5000;
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int p = i;
            threads[i] = new Thread(() -> {
                try {
                    while (System.currentTimeMillis() < end) {
                        c.eatOnce(p, 2);
                    }
                } catch (InterruptedException ignored) {
                }
            });
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join(20_000);
        }
        int min = IntStream.range(0, n).map(i -> c.meals[i].get()).min().getAsInt();
        int max = IntStream.range(0, n).map(i -> c.meals[i].get()).max().getAsInt();
        assertEquals(0, c.violations.get());
        assertTrue(min > 0, "un philosophe n'a jamais mangé (famine)");
        assertTrue(max <= 4 * min, "répartition trop inégale : min=" + min + " max=" + max);
    }

    @Test
    void simultaneousRequests_withSameTimestamp_areOrderedById() throws Exception {
        // Tous les philosophes prennent la même valeur d'horloge (ts=1) en même temps :
        // le départage par identifiant doit éviter tout blocage.
        SimulatedCluster c = new SimulatedCluster(5, 3000);
        c.maxDelayMs = 0;
        AtomicInteger finished = new AtomicInteger();
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int p = i;
            threads[i] = new Thread(() -> {
                try {
                    c.eatOnce(p, 1);
                    finished.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            });
            threads[i].setDaemon(true);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(20_000);
        assertEquals(5, finished.get());
        assertEquals(0, c.violations.get());
    }
}
