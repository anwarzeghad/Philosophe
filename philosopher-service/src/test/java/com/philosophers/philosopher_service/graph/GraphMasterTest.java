package com.philosophers.philosopher_service.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires du Master avec des workers simulés (WorkerClient) : partitionnement,
 * absence de double comptage, pannes, lenteurs, résultats en double.
 */
class GraphMasterTest {

    /** Worker honnête : calcule vraiment les degrés du lot reçu. */
    private static final WorkerClient HONEST = (worker, task) ->
            DegreeComputation.compute(task.taskId(), task.nodes(), task.edges());

    private static void assertSameAsSequential(int nodes, int[] edges, GraphMaster.Report r) {
        TaskResult ref = DegreeComputation.compute(0, nodes, edges);
        assertArrayEquals(ref.out(), r.out());
        assertArrayEquals(ref.in(), r.in());
    }

    // ------------------------------------------------------------- calcul de base

    @Test
    void degreesOfSmallGraph() {
        // 0->1, 0->2, 1->2, 2->0
        int[] edges = {0, 1, 0, 2, 1, 2, 2, 0};
        TaskResult r = DegreeComputation.compute(0, 3, edges);
        assertArrayEquals(new int[]{2, 1, 1}, r.out());
        assertArrayEquals(new int[]{1, 1, 2}, r.in());
    }

    @Test
    void invalidTask_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DegreeComputation.compute(0, 3, new int[]{0, 5}));
        assertThrows(IllegalArgumentException.class, () -> DegreeComputation.compute(0, 3, new int[]{0, 1, 2}));
    }

    // ------------------------------------------------------------- partitionnement

    @Test
    void partition_coversEveryEdgeExactlyOnce() {
        int[] edges = RandomGraph.edges(50, 1003, 1);   // 1003 : ne se divise pas par 7
        List<TaskRequest> tasks = GraphMaster.partition(50, edges, 7);
        assertEquals(7, tasks.size());
        int total = tasks.stream().mapToInt(t -> t.edges().length / 2).sum();
        assertEquals(1003, total, "aucune arête perdue ni dupliquée");
        // les chunks mis bout à bout redonnent exactement la liste d'arêtes
        int pos = 0;
        for (TaskRequest t : tasks) {
            for (int x : t.edges()) {
                assertEquals(edges[pos++], x);
            }
        }
    }

    @Test
    void partition_moreChunksThanEdges_isCapped() {
        List<TaskRequest> tasks = GraphMaster.partition(5, new int[]{0, 1, 1, 2}, 10);
        assertEquals(2, tasks.size());
    }

    @Test
    void partition_emptyGraph_givesOneEmptyTask() {
        List<TaskRequest> tasks = GraphMaster.partition(5, new int[0], 4);
        assertEquals(1, tasks.size());
        assertEquals(0, tasks.get(0).edges().length);
    }

    @Test
    void edgeBetweenPartitions_isCountedOnce() throws Exception {
        // Deux arêtes seulement, chacune dans un chunk différent, qui touchent les mêmes nœuds :
        // 0->1 (chunk 0) et 1->0 (chunk 1). Chaque nœud doit avoir degré 2 (1 sortant + 1 entrant), pas 4.
        int[] edges = {0, 1, 1, 0};
        GraphMaster master = new GraphMaster(HONEST, 2);
        GraphMaster.Report r = master.compute(2, edges, List.of(1, 2), 2);
        assertEquals(2, r.degree(0));
        assertEquals(2, r.degree(1));
    }

    // ------------------------------------------------------------- correction

    @Test
    void parallelResult_equalsSequential_forAnyNumberOfWorkersAndChunks() throws Exception {
        int nodes = 200;
        int[] edges = RandomGraph.edges(nodes, 5000, 7);
        for (int workers = 1; workers <= 5; workers++) {
            for (int chunks : new int[]{1, 3, 8, 50}) {
                GraphMaster master = new GraphMaster(HONEST, 2);
                List<Integer> ids = java.util.stream.IntStream.rangeClosed(1, workers).boxed().toList();
                GraphMaster.Report r = master.compute(nodes, edges, ids, chunks);
                assertSameAsSequential(nodes, edges, r);
                long sum = 0;
                for (int v = 0; v < nodes; v++) sum += r.degree(v);
                assertEquals(2L * 5000, sum, "somme des degrés = 2 x arêtes");
            }
        }
    }

    @Test
    void dynamicBalancing_fastWorkerTakesMoreChunks() throws Exception {
        Map<Integer, AtomicInteger> perWorker = new ConcurrentHashMap<>();
        WorkerClient client = (worker, task) -> {
            perWorker.computeIfAbsent(worker, w -> new AtomicInteger()).incrementAndGet();
            if (worker == 2) {
                try {
                    Thread.sleep(80);   // worker 2 lent
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return DegreeComputation.compute(task.taskId(), task.nodes(), task.edges());
        };
        int[] edges = RandomGraph.edges(100, 2000, 3);
        GraphMaster.Report r = new GraphMaster(client, 2).compute(100, edges, List.of(1, 2), 20);
        assertSameAsSequential(100, edges, r);
        assertTrue(perWorker.get(1).get() > perWorker.get(2).get(),
                "le worker rapide doit traiter plus de chunks : " + perWorker);
    }

    // ------------------------------------------------------------- pannes

    @Test
    void failingWorker_chunksAreRedoneByOthers() throws Exception {
        WorkerClient client = (worker, task) -> {
            if (worker == 1) {
                throw new IllegalStateException("worker 1 en panne");
            }
            return DegreeComputation.compute(task.taskId(), task.nodes(), task.edges());
        };
        int[] edges = RandomGraph.edges(100, 3000, 5);
        GraphMaster.Report r = new GraphMaster(client, 2).compute(100, edges, List.of(1, 2, 3), 12);
        assertSameAsSequential(100, edges, r);
        assertEquals(List.of(1), r.deadWorkers(), "le worker 1 est déclaré hors service");
        assertTrue(r.retries() >= 2);
        assertEquals(0, r.localTasks(), "les workers 2 et 3 suffisent, pas de repli");
    }

    @Test
    void allWorkersDown_masterComputesLocally() throws Exception {
        WorkerClient client = (worker, task) -> {
            throw new IllegalStateException("réseau coupé");
        };
        int[] edges = RandomGraph.edges(100, 3000, 9);
        GraphMaster.Report r = new GraphMaster(client, 2).compute(100, edges, List.of(1, 2), 6);
        assertSameAsSequential(100, edges, r);
        assertEquals(6, r.localTasks());
        assertEquals(2, r.deadWorkers().size());
    }

    @Test
    void noWorkerAtAll_masterComputesEverything() throws Exception {
        int[] edges = RandomGraph.edges(50, 500, 2);
        GraphMaster.Report r = new GraphMaster(HONEST, 2).compute(50, edges, List.of(), 4);
        assertSameAsSequential(50, edges, r);
        assertEquals(4, r.localTasks());
    }

    @Test
    void transientFailure_isRetriedAndWorkerSurvives() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WorkerClient client = (worker, task) -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("erreur isolée");   // une seule panne passagère
            }
            return DegreeComputation.compute(task.taskId(), task.nodes(), task.edges());
        };
        int[] edges = RandomGraph.edges(80, 1000, 4);
        GraphMaster.Report r = new GraphMaster(client, 2).compute(80, edges, List.of(1), 5);
        assertSameAsSequential(80, edges, r);
        assertEquals(1, r.retries());
        assertTrue(r.deadWorkers().isEmpty(), "un seul échec ne suffit pas à déclarer le worker mort");
    }

    @Test
    void duplicateOrLateResults_areNotCountedTwice() throws Exception {
        // Le worker 1 répond lentement ET échoue côté master (exception après calcul) :
        // le chunk est refait ailleurs. Quel que soit le résultat qui arrive, un seul est gardé.
        AtomicInteger first = new AtomicInteger();
        WorkerClient client = (worker, task) -> {
            TaskResult r = DegreeComputation.compute(task.taskId(), task.nodes(), task.edges());
            if (worker == 1 && first.getAndIncrement() < 3) {
                throw new IllegalStateException("réponse perdue après calcul");
            }
            return r;
        };
        int[] edges = RandomGraph.edges(60, 1200, 11);
        GraphMaster.Report r = new GraphMaster(client, 5).compute(60, edges, List.of(1, 2), 6);
        assertSameAsSequential(60, edges, r);
    }

    @Test
    void largeGraph_manyChunks_isExact() throws Exception {
        int nodes = 5000;
        int[] edges = RandomGraph.edges(nodes, 200_000, 99);
        GraphMaster.Report r = new GraphMaster(HONEST, 2).compute(nodes, edges, List.of(1, 2, 3, 4), 64);
        assertSameAsSequential(nodes, edges, r);
    }
}
