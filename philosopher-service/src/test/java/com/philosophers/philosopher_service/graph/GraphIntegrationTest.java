package com.philosophers.philosopher_service.graph;

import com.philosophers.philosopher_service.PhilosopherServiceApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration Master-Worker : de vrais microservices philosophes Spring Boot.
 * Le philosophe 0 est le master, les autres sont les workers ; tout passe par HTTP (RestTemplate).
 * Le dernier test mesure les temps d'exécution en faisant varier workers et taille du graphe.
 */
@Timeout(300)
class GraphIntegrationTest {

    private static final int BASE_PORT = 19900;

    private final ConfigurableApplicationContext[] contexts = new ConfigurableApplicationContext[6];
    private final RestTemplate http = new RestTemplate();

    /** Démarre le philosophe id ; "extra" : arguments supplémentaires (--cle=valeur). */
    private void start(int id, String... extra) {
        String[] args = new String[5 + extra.length];
        args[0] = "--server.port=" + (BASE_PORT + id);
        args[1] = "--philosopher.id=" + id;
        args[2] = "--philosophers.total=6";
        args[3] = "--philosophers.base-port=" + BASE_PORT;
        args[4] = "--philosopher.mode=none";
        System.arraycopy(extra, 0, args, 5, extra.length);
        contexts[id] = new SpringApplicationBuilder(PhilosopherServiceApplication.class).run(args);
    }

    @AfterEach
    void stopAll() {
        for (int i = 0; i < contexts.length; i++) {
            if (contexts[i] != null) {
                contexts[i].close();
                contexts[i] = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compute(String query) {
        return http.getForObject("http://localhost:" + BASE_PORT + "/graph/compute?" + query, Map.class);
    }

    @Test
    void masterAndWorkers_computeDegreesOverHttp() {
        for (int i = 0; i < 4; i++) start(i);   // master 0 + workers 1,2,3

        Map<String, Object> r = compute("nodes=5&edges=12&seed=1&workers=1,2,3&chunks=3");
        assertEquals(true, r.get("correct"));
        assertEquals(true, r.get("degreeSumIsTwiceEdges"));
        assertEquals(0, ((List<?>) r.get("deadWorkers")).size());
        assertEquals(0, r.get("tasksComputedByMaster"));
        // petit graphe : les degrés sont renvoyés, leur somme vaut 2 x 12
        long sum = ((List<?>) r.get("degrees")).stream().mapToLong(x -> ((Number) x).longValue()).sum();
        assertEquals(24, sum);
    }

    @Test
    void largerGraph_isExactOverHttp() {
        for (int i = 0; i < 5; i++) start(i);
        Map<String, Object> r = compute("nodes=20000&edges=300000&workers=1,2,3,4&chunks=16");
        assertEquals(true, r.get("correct"));
        assertEquals(true, r.get("degreeSumIsTwiceEdges"));
    }

    @Test
    void workerNotStarted_tasksAreRedoneByOthers() {
        start(0);
        start(1);
        start(3);   // le worker 2 n'a jamais démarré (indisponible)
        Map<String, Object> r = compute("nodes=1000&edges=20000&workers=1,2,3&chunks=9");
        assertEquals(true, r.get("correct"), "résultat exact malgré le worker 2 absent");
        assertTrue(((Number) r.get("retries")).intValue() >= 1, "au moins une tâche a dû être relancée");
    }

    @Test
    void slowWorker_isTimedOutAndItsTasksAreRelaunched() {
        start(0, "--graph.task-timeout-ms=500");   // le master n'attend un worker que 500 ms
        start(1);
        start(2, "--graph.worker.delay-ms=3000");   // worker 2 : 3 s par tâche, bien au-delà du délai
        Map<String, Object> r = compute("nodes=500&edges=5000&workers=1,2&chunks=6");
        assertEquals(true, r.get("correct"), "le résultat tardif du worker 2 ne doit pas fausser le total");
        assertTrue(((Number) r.get("retries")).intValue() >= 1, "la tâche trop lente a été relancée");
    }

    @Test
    void allWorkersUnavailable_masterFallsBackToLocalComputation() {
        start(0);   // aucun worker démarré
        Map<String, Object> r = compute("nodes=300&edges=3000&workers=1,2&chunks=4");
        assertEquals(true, r.get("correct"));
        assertEquals(4, r.get("tasksComputedByMaster"));
    }

    @Test
    void invalidParameters_areRejected() {
        start(0);
        assertThrows(org.springframework.web.client.HttpClientErrorException.BadRequest.class,
                () -> compute("nodes=0&edges=10"));
        assertThrows(org.springframework.web.client.HttpClientErrorException.BadRequest.class,
                () -> compute("nodes=10&edges=10&workers=a,b"));
    }

    @Test
    void invalidTaskSentToWorker_isRejectedWith400() {
        start(1);
        TaskRequest bad = new TaskRequest(0, 3, new int[]{0, 9});   // nœud 9 hors bornes
        assertThrows(org.springframework.web.client.HttpClientErrorException.BadRequest.class,
                () -> http.postForObject("http://localhost:" + (BASE_PORT + 1) + "/graph/task", bad, TaskResult.class));
    }

    /** Performances : temps d'exécution selon le nombre de workers et la taille du graphe. */
    @Test
    void performance_variesWithWorkersAndGraphSize() {
        for (int i = 0; i < 5; i++) start(i);
        int[][] sizes = {{10_000, 100_000}, {50_000, 1_000_000}, {100_000, 3_000_000}};
        int[] workerCounts = {1, 2, 3, 4};

        StringBuilder table = new StringBuilder("\n=== Performances Master-Worker (ms, via HTTP/localhost) ===\n");
        table.append(String.format("%-22s %-12s", "graphe (noeuds/aretes)", "sequentiel"));
        for (int w : workerCounts) table.append(String.format(" %-9s", w + " worker" + (w > 1 ? "s" : "")));
        table.append('\n');

        compute("nodes=1000&edges=10000&workers=1,2,3,4");   // échauffement (JIT, connexions)
        for (int[] size : sizes) {
            long seq = 0;
            StringBuilder line = new StringBuilder();
            for (int w : workerCounts) {
                String workers = switch (w) {
                    case 1 -> "1";
                    case 2 -> "1,2";
                    case 3 -> "1,2,3";
                    default -> "1,2,3,4";
                };
                Map<String, Object> r = compute("nodes=" + size[0] + "&edges=" + size[1]
                        + "&workers=" + workers + "&chunks=" + (4 * w));
                assertEquals(true, r.get("correct"));
                seq = ((Number) r.get("sequentialMs")).longValue();
                line.append(String.format(" %-9d", ((Number) r.get("parallelMs")).longValue()));
            }
            table.append(String.format("%-22s %-12d", size[0] + " / " + size[1], seq)).append(line).append('\n');
        }
        System.out.println(table);
    }
}
