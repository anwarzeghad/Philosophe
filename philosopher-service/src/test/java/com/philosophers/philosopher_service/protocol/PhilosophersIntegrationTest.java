package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.PhilosopherServiceApplication;
import com.philosophers.philosopher_service.dto.RequestMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration : plusieurs microservices philosophes Spring Boot réels (un contexte
 * par philosophe, serveur HTTP sur son propre port) échangent leurs messages par HTTP.
 * Le mode "none" désactive la boucle automatique ; les tests pilotent les philosophes.
 */
@org.junit.jupiter.api.Timeout(100)
class PhilosophersIntegrationTest {

    // Chaque test utilise ses propres ports : des messages tardifs d'un test précédent
    // (horloges logiques repartant de 0) ne doivent jamais atteindre le cluster suivant.
    private static final AtomicInteger NEXT_BASE = new AtomicInteger(19700);
    private final int BASE_PORT = NEXT_BASE.getAndAdd(10);

    private final ConfigurableApplicationContext[] contexts = new ConfigurableApplicationContext[8];
    private final RestTemplate http = new RestTemplate();

    private void start(int id, int total) {
        contexts[id] = new SpringApplicationBuilder(PhilosopherServiceApplication.class)
                // arguments de ligne de commande : priorité sur application.properties
                .run("--server.port=" + (BASE_PORT + id),
                        "--philosopher.id=" + id,
                        "--philosophers.total=" + total,
                        "--philosophers.base-port=" + BASE_PORT,
                        "--philosopher.mode=none");
    }

    private void stop(int id) {
        if (contexts[id] != null) {
            contexts[id].close();
            contexts[id] = null;
        }
    }

    private ForkProtocol node(int id) {
        return contexts[id].getBean(ForkProtocol.class);
    }

    @AfterEach
    void stopAll() {
        for (int i = 0; i < contexts.length; i++) {
            stop(i);
        }
    }

    private void awaitTrue(java.util.function.BooleanSupplier cond, long timeoutMs, String message)
            throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail(message);
    }

    @Test
    void httpEndpoint_requestMakesTokenHolderTransferToken() throws Exception {
        for (int i = 0; i < 3; i++) start(i, 3);

        // P2 (fourchettes F2,F0) envoie sa demande à P0 par HTTP : P0 doit lui céder F0 par HTTP
        http.postForObject("http://localhost:" + BASE_PORT + "/protocol/request",
                new RequestMessage(2, 1), Void.class);

        awaitTrue(() -> node(2).holdsToken(0), 5000, "P2 doit recevoir le jeton F0");
        assertFalse(node(0).holdsToken(0), "le jeton est unique : P0 ne l'a plus");
        assertTrue(node(2).holdsToken(2), "P2 garde son propre jeton F2");
    }

    @Test
    void broadcast_reachesAllPeers_andTokensTravelOverHttp() throws Exception {
        for (int i = 0; i < 4; i++) start(i, 4);

        // P3 a faim : sa demande est diffusée à P0, P1, P2 ; il doit recevoir F0 de P0
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.submit(() -> {
            node(3).acquireForks();
            return null;
        });
        awaitTrue(() -> node(3).isEating(), 8000, "P3 doit obtenir F3 et F0 et manger");
        // la demande a bien été mémorisée par les autres philosophes (diffusion)
        assertEquals(1, node(1).pendingCount());
        assertEquals(1, node(2).pendingCount());
        node(3).releaseForks();
        // FINISHED diffusé : les demandes sont effacées partout
        awaitTrue(() -> node(1).pendingCount() == 0 && node(2).pendingCount() == 0 && node(0).pendingCount() == 0,
                5000, "FINISHED doit effacer la demande chez tous");
        ex.shutdownNow();
    }

    @org.junit.jupiter.api.RepeatedTest(8)
    void concurrentPhilosophers_overHttp_neverShareAFork() throws Exception {
        int n = 5;
        for (int i = 0; i < n; i++) start(i, n);

        AtomicInteger[] users = new AtomicInteger[n];
        AtomicInteger[] meals = new AtomicInteger[n];
        for (int i = 0; i < n; i++) {
            users[i] = new AtomicInteger();
            meals[i] = new AtomicInteger();
        }
        AtomicInteger violations = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int p = i;
            pool.submit(() -> {
                try {
                    go.await();
                    for (int m = 0; m < 6; m++) {
                        ForkProtocol f = node(p);
                        f.acquireForks();
                        if (users[p].incrementAndGet() > 1) violations.incrementAndGet();
                        if (users[(p + 1) % n].incrementAndGet() > 1) violations.incrementAndGet();
                        Thread.sleep(20);
                        meals[p].incrementAndGet();
                        users[(p + 1) % n].decrementAndGet();
                        users[p].decrementAndGet();
                        f.releaseForks();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        go.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        if (!finished) {
            StringBuilder dump = new StringBuilder();
            for (int i = 0; i < n; i++) dump.append("\n").append(node(i).describeState());
            fail("interblocage ou famine :" + dump);
        }
        pool.shutdownNow();
        assertEquals(0, violations.get());
        for (int i = 0; i < n; i++) {
            assertEquals(6, meals[i].get());
        }
    }

    @Test
    void resilience_stoppedPhilosopher_neighbourWaits_thenEatsWhenBack() throws Exception {
        for (int i = 0; i < 3; i++) start(i, 3);
        stop(2);   // P2 indisponible : il détient F2, nécessaire à P1 (F1,F2)

        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.submit(() -> {
            node(1).acquireForks();
            return null;
        });

        Thread.sleep(1500);
        assertFalse(node(1).isEating(), "P1 ne peut pas manger sans le jeton F2");

        // P0 n'a pas besoin de P2 : il continue de manger (F0 + F1)... après que P1 ait fini ; ici
        // on vérifie seulement que P1 attend sans planter, puis redémarre P2
        start(2, 3);
        awaitTrue(() -> node(1).isEating(), 20_000,
                "P1 doit manger après le retour de P2 (retransmission de la demande)");
        node(1).releaseForks();
        ex.shutdownNow();
    }

    @Test
    void resilience_philosopherIndependentOfDownNode_keepsEating() throws Exception {
        for (int i = 0; i < 3; i++) start(i, 3);
        stop(2);   // P0 utilise F0,F1 : il n'a pas besoin de P2

        for (int meal = 0; meal < 3; meal++) {
            node(0).acquireForks();
            assertTrue(node(0).isEating());
            node(0).releaseForks();
        }
    }
}
