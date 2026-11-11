package com.philosophers.supervisor_service;

import com.philosophers.supervisor_service.controller.SupervisorController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partie 3.1 : demande des deux fourchettes en une seule requête, SANS verrou.
 */
class SupervisorWithoutLockTest {

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
     * Test de concurrence : deux philosophes voisins (P0 : fourchettes 0 et 1,
     * P1 : fourchettes 1 et 2) demandent en même temps. Ils partagent la fourchette 1,
     * donc un seul des deux doit obtenir ses fourchettes.
     * Sans verrou, la vérification et la réservation ne sont pas atomiques : on compte
     * les essais où les deux ont réussi (fourchette 1 attribuée deux fois).
     * Le résultat est affiché, non asserté, car il dépend de l'ordonnancement.
     */
    @Test
    void concurrentNeighbours_withoutLock_canShareAFork() throws Exception {
        final int trials = 2000;
        int violations = 0;

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

                if (winners.get() == 2) {
                    violations++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        System.out.println("3.1 sans verrou : fourchette partagée dans " + violations + "/" + trials + " essais");
        assertTrue(violations >= 0);
    }
}
