package com.philosophers.philosopher_service.graph;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rôle Master : calcule le degré de chaque nœud d'un grand graphe en répartissant le travail
 * entre les workers.
 *
 * Partitionnement : par lots d'arêtes.
 * La liste des arêtes est découpée en "chunks" consécutifs ; chaque arête appartient à
 * exactement un chunk. Un worker reçoit uniquement le nombre de nœuds et les arêtes de son
 * chunk. Il renvoie, pour chaque nœud, le nombre d'arêtes sortantes et entrantes DE SON CHUNK.
 *
 * Pas de double comptage.
 * Une arête (u,v) peut relier deux nœuds "de partitions différentes" : c'est le piège du
 * partitionnement par nœuds (l'arête serait vue par les deux workers). Ici on partitionne les
 * ARÊTES : chaque arête est traitée une seule fois, par un seul worker, qui ajoute 1 à out[u]
 * et 1 à in[v]. Le master additionne ensuite les tableaux partiels : comme les chunks sont
 * disjoints, la somme est exacte. Contrôle : somme des degrés = 2 x nombre d'arêtes.
 *
 * Répartition dynamique et pannes.
 * Il y a plus de chunks que de workers. Chaque worker a un thread côté master qui prend le
 * prochain chunk dans une file partagée (un worker rapide traite donc plus de chunks).
 * Si l'appel échoue (indisponible, erreur, délai dépassé), le chunk est remis dans la file
 * et sera repris par un autre worker ; un worker qui échoue plusieurs fois de suite est
 * déclaré hors service. Si plus aucun worker ne répond, le master calcule lui-même les chunks
 * restants. Les résultats sont rangés par identifiant de chunk et seul le premier est gardé :
 * un résultat tardif ou en double ne peut pas être compté deux fois.
 */
@Component
public class GraphMaster {

    /** Résultat final et statistiques d'exécution. */
    public record Report(int[] out, int[] in, int tasks, int retries, int localTasks, List<Integer> deadWorkers) {
        public long degree(int node) {
            return (long) out[node] + in[node];
        }
    }

    private final WorkerClient client;
    private final int maxFailures;

    @Autowired
    public GraphMaster(WorkerClient client, @Value("${graph.max-failures:2}") int maxFailures) {
        this.client = client;
        this.maxFailures = maxFailures;
    }

    /**
     * @param edges   arêtes à plat [u0, v0, u1, v1, ...]
     * @param workers identifiants des philosophes workers
     * @param chunks  nombre de lots d'arêtes (tâches)
     */
    public Report compute(int nodes, int[] edges, List<Integer> workers, int chunks) throws InterruptedException {
        List<TaskRequest> tasks = partition(nodes, edges, chunks);

        BlockingQueue<TaskRequest> queue = new LinkedBlockingQueue<>(tasks);
        ConcurrentMap<Integer, TaskResult> results = new ConcurrentHashMap<>();
        AtomicInteger remaining = new AtomicInteger(tasks.size());
        AtomicInteger retries = new AtomicInteger();
        Set<Integer> dead = ConcurrentHashMap.newKeySet();

        List<Thread> threads = new ArrayList<>();
        for (int worker : workers) {
            Thread t = new Thread(() -> serve(worker, queue, results, remaining, retries, dead));
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        // Repli : les chunks sans résultat (tous les workers sont tombés) sont calculés ici
        int local = 0;
        for (TaskRequest task : tasks) {
            if (!results.containsKey(task.taskId())) {
                results.put(task.taskId(), DegreeComputation.compute(task.taskId(), nodes, task.edges()));
                local++;
            }
        }

        // Fusion des résultats partiels
        int[] out = new int[nodes];
        int[] in = new int[nodes];
        for (TaskRequest task : tasks) {
            TaskResult r = results.get(task.taskId());
            for (int v = 0; v < nodes; v++) {
                out[v] += r.out()[v];
                in[v] += r.in()[v];
            }
        }
        return new Report(out, in, tasks.size(), retries.get(), local, new ArrayList<>(dead));
    }

    /** Découpe les arêtes en chunks disjoints et consécutifs. */
    static List<TaskRequest> partition(int nodes, int[] edges, int chunks) {
        int edgeCount = edges.length / 2;
        int count = Math.max(1, Math.min(chunks, Math.max(1, edgeCount)));
        int size = (edgeCount + count - 1) / count;   // arêtes par chunk (arrondi au-dessus)
        List<TaskRequest> tasks = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            int from = Math.min(edgeCount, id * size);
            int to = Math.min(edgeCount, (id + 1) * size);
            tasks.add(new TaskRequest(id, nodes, Arrays.copyOfRange(edges, 2 * from, 2 * to)));
        }
        return tasks;
    }

    /** Boucle du thread qui sert un worker : prend un chunk, l'envoie, range le résultat. */
    private void serve(int worker, BlockingQueue<TaskRequest> queue, ConcurrentMap<Integer, TaskResult> results,
                       AtomicInteger remaining, AtomicInteger retries, Set<Integer> dead) {
        int failuresInARow = 0;
        try {
            while (remaining.get() > 0) {
                TaskRequest task = queue.poll(50, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;   // rien à prendre : attendre les chunks remis par d'éventuels échecs
                }
                try {
                    TaskResult result = client.execute(worker, task);
                    failuresInARow = 0;
                    if (results.putIfAbsent(task.taskId(), result) == null) {
                        remaining.decrementAndGet();   // premier résultat pour ce chunk
                    }
                } catch (RuntimeException e) {
                    retries.incrementAndGet();
                    queue.add(task);   // un autre worker (ou le repli local) le reprendra
                    if (++failuresInARow >= maxFailures) {
                        dead.add(worker);
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
