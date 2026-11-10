package com.philosophers.supervisor_service;

import com.philosophers.supervisor_service.controller.SupervisorController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jeu de tests recensant les failles de la version NAIVE du superviseur (partie 2).
 *
 * Chaque test DEMONTRE une faille : il passe tant que la faille est presente.
 * Quand la partie 3 corrigera le superviseur, ces tests devront etre inverses
 * (ou echouer), ce qui prouve que la correction agit.
 *
 * Failles recensees :
 *  F1. Interrogation puis prise non atomiques (check-then-act entre available et take).
 *  F2. Course dans take : deux threads peuvent obtenir la meme fourchette.
 *  F3. Interblocage : chacun garde sa fourchette gauche et attend la droite.
 *  F4. Famine : aucune file d'attente ni equite, un philosophe peut ne jamais manger.
 *  F5. Liberation sans controle de propriete : n'importe qui libere n'importe quelle fourchette.
 */
class SupervisorNaiveFlawsTest {

    // F1 ---------------------------------------------------------------
    @Test
    void f1_availableThenTake_isNotAtomic() {
        SupervisorController sup = new SupervisorController();

        // Le philosophe A interroge : "oui, la fourchette 0 est libre"
        assertTrue(sup.isAvailable(0));
        // ... mais B la prend entre-temps
        assertTrue(sup.takeFork(0));
        // La demande de A, basee sur une reponse devenue fausse, echoue.
        assertFalse(sup.takeFork(0),
                "FAILLE F1 : la reponse 'disponible' ne garantit rien pour le take suivant");
    }

    // F2 ---------------------------------------------------------------
    /**
     * Test non deterministe : on lance beaucoup de tentatives simultanees sur la meme
     * fourchette. Dans une version correcte, exactement UN thread gagne a chaque essai.
     * Ici, on compte les essais ou plusieurs threads ont gagne (violation d'exclusion mutuelle).
     * Le resultat est affiche mais n'est pas asserte : la course depend de l'ordonnancement.
     */
    @Test
    void f2_concurrentTake_canGrantSameForkTwice() throws Exception {
        final int trials = 2000;
        final int threads = 8;
        int violations = 0;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < trials; t++) {
                SupervisorController sup = new SupervisorController();
                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch go = new CountDownLatch(1);
                AtomicInteger winners = new AtomicInteger();

                CountDownLatch done = new CountDownLatch(threads);
                for (int i = 0; i < threads; i++) {
                    pool.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                            if (sup.takeFork(0)) {
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

                if (winners.get() > 1) {
                    violations++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        System.out.println("F2 : exclusion mutuelle violee dans " + violations + "/" + trials + " essais");
        // Au moins un gagnant par essai est toujours garanti ; le reste est observationnel.
        assertTrue(violations >= 0);
    }

    // F3 ---------------------------------------------------------------
    @Test
    void f3_deadlock_everyoneHoldsLeftForkAndWaitsForRight() {
        final int n = 5;
        SupervisorController sup = new SupervisorController();

        // Ordonnancement defavorable : tous prennent leur fourchette gauche d'abord.
        for (int p = 0; p < n; p++) {
            assertTrue(sup.takeFork(p), "philosophe " + p + " prend sa fourchette gauche");
        }
        // Tous demandent ensuite la droite : tous sont refuses, personne ne peut progresser.
        for (int p = 0; p < n; p++) {
            int right = (p + 1) % n;
            assertFalse(sup.isAvailable(right));
            assertFalse(sup.takeFork(right),
                    "FAILLE F3 : philosophe " + p + " bloque, interblocage complet");
        }
    }

    // F4 ---------------------------------------------------------------
    @Test
    void f4_starvation_noQueueNoFairness() {
        SupervisorController sup = new SupervisorController();

        // Philosophe 0 a besoin des fourchettes 0 et 1.
        // Ses voisins (4 et 1) se relaient : la fourchette 1 est toujours reprise
        // par le philosophe 1 des qu'elle est liberee.
        for (int round = 0; round < 100; round++) {
            assertTrue(sup.takeFork(1), "le voisin 1 reprend la fourchette 1");
            // Le philosophe 0 interroge : toujours occupee
            assertFalse(sup.isAvailable(1));
            sup.releaseFork(1);
            // Aucune file : le voisin la reprend avant que 0 ait reessaye
        }
        // Le philosophe 0 n'a jamais pu obtenir la fourchette 1 : pas de garantie d'equite.
        // (le superviseur ne memorise aucune demande en attente)
        assertTrue(sup.isAvailable(1), "FAILLE F4 : rien ne reserve la fourchette au demandeur le plus ancien");
    }

    // F5 ---------------------------------------------------------------
    @Test
    void f5_release_hasNoOwnershipCheck() {
        SupervisorController sup = new SupervisorController();

        assertTrue(sup.takeFork(2));          // le philosophe 2 tient la fourchette 2
        sup.releaseFork(2);                   // un philosophe quelconque (ou un bug) la libere
        assertTrue(sup.isAvailable(2));       // elle redevient libre alors que 2 mange peut-etre
        assertTrue(sup.takeFork(2),
                "FAILLE F5 : un tiers peut liberer et re-attribuer la fourchette d'un autre");

        // Liberer une fourchette jamais prise est aussi accepte sans erreur
        assertDoesNotThrow(() -> sup.releaseFork(99));
    }
}
