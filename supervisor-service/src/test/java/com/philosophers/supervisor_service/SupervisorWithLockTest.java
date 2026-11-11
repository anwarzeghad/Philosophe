package com.philosophers.supervisor_service;

import com.philosophers.supervisor_service.controller.SupervisorController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partie 3.2 : demande des deux fourchettes avec verrou sur la section critique.
 */
class SupervisorWithLockTest {

    @Test
    void takeBoth_whenBothFree_isGranted() {
        SupervisorController sup = new SupervisorController();
        assertTrue(sup.takeBothForks(0, 1));
        assertFalse(sup.isAvailable(0));
        assertFalse(sup.isAvailable(1));
    }

    @Test
    void takeBoth_whenOneIsTaken_isRefusedAndReservesNothing() {
        SupervisorController sup = new SupervisorController();
        assertTrue(sup.takeFork(1));
        assertFalse(sup.takeBothForks(0, 1));
        // Tout ou rien : la fourchette 0 ne doit pas avoir été réservée
        assertTrue(sup.isAvailable(0));
    }

    @Test
    void takeBoth_afterRelease_isGrantedAgain() {
        SupervisorController sup = new SupervisorController();
        assertTrue(sup.takeBothForks(0, 1));
        sup.releaseFork(0);
        sup.releaseFork(1);
        assertTrue(sup.takeBothForks(0, 1));
    }

    /**
     * Deux philosophes voisins (fourchettes 0-1 et 1-2) demandent en même temps.
     * Ils partagent la fourchette 1 : avec le verrou, un seul des deux doit gagner,
     * à chaque essai (en 3.1 sans verrou, ce test échouait environ 15 fois sur 2000).
     */
    @Test
    void concurrentNeighbours_withLock_neverShareAFork() throws Exception {
        final int trials = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int t = 0; t < trials; t++) {
                SupervisorController sup = new SupervisorController();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(2);
                AtomicInteger winners = new AtomicInteger();

                int[][] requests = {{0, 1}, {1, 2}};
                for (int[] r : requests) {
                    pool.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                            if (sup.takeBothForks(r[0], r[1])) {
                                winners.incrementAndGet();
                            }
                        } catch (InterruptedException ignored) {
                        } finally {
                            done.countDown();
                        }
                    });
                }
                ready.await();
                go.countDown();
                done.await();

                assertEquals(1, winners.get(), "essai " + t + " : exactement un des deux voisins doit gagner");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 5 philosophes en anneau, plusieurs threads par philosophe : chacun demande ses
     * deux fourchettes, "mange", puis les libère. On compte en permanence combien de
     * philosophes utilisent chaque fourchette : ce nombre ne doit jamais dépasser 1.
     */
    @Test
    void ring_manyThreads_noForkUsedByTwoAtOnce() throws Exception {
        final int n = 5;
        final int threads = 10;
        final int iterations = 3000;
        SupervisorController sup = new SupervisorController();
        AtomicInteger[] users = new AtomicInteger[n];
        for (int i = 0; i < n; i++) {
            users[i] = new AtomicInteger();
        }
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger meals = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int th = 0; th < threads; th++) {
            final int philosopher = th % n;
            pool.submit(() -> {
                int left = philosopher;
                int right = (philosopher + 1) % n;
                try {
                    go.await();
                    for (int i = 0; i < iterations; i++) {
                        if (sup.takeBothForks(left, right)) {
                            if (users[left].incrementAndGet() > 1) violations.incrementAndGet();
                            if (users[right].incrementAndGet() > 1) violations.incrementAndGet();
                            meals.incrementAndGet();
                            users[right].decrementAndGet();
                            users[left].decrementAndGet();
                            sup.releaseFork(right);
                            sup.releaseFork(left);
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        go.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "les threads doivent terminer (pas d'interblocage)");
        pool.shutdownNow();

        assertEquals(0, violations.get(), "une fourchette a été utilisée par deux philosophes à la fois");
        assertTrue(meals.get() > 0, "au moins un repas doit avoir eu lieu");
    }
}
