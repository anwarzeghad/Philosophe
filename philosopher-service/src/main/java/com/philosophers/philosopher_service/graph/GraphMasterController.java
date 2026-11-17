package com.philosophers.philosopher_service.graph;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rôle Master : le philosophe appelé sur GET /graph/compute devient le master.
 * Exemple : curl "http://localhost:9000/graph/compute?nodes=100000&edges=1000000&workers=1,2,3,4"
 */
@RestController
@RequestMapping("/graph")
public class GraphMasterController {

    private final GraphMaster master;

    @Value("${philosopher.id:0}")
    private int id;

    @Value("${philosophers.total:5}")
    private int total;

    public GraphMasterController(GraphMaster master) {
        this.master = master;
    }

    @GetMapping("/compute")
    public Map<String, Object> compute(@RequestParam(defaultValue = "1000") int nodes,
                                       @RequestParam(defaultValue = "10000") int edges,
                                       @RequestParam(defaultValue = "42") long seed,
                                       @RequestParam(required = false) Integer chunks,
                                       @RequestParam(required = false) String workers) throws InterruptedException {
        if (nodes < 1 || edges < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nodes >= 1 et edges >= 0 requis");
        }
        List<Integer> workerIds = parseWorkers(workers);
        int chunkCount = chunks != null ? chunks : Math.max(1, 2 * workerIds.size());

        int[] graph = RandomGraph.edges(nodes, edges, seed);

        long start = System.nanoTime();
        GraphMaster.Report report = master.compute(nodes, graph, workerIds, chunkCount);
        long parallelMs = (System.nanoTime() - start) / 1_000_000;

        start = System.nanoTime();
        TaskResult reference = DegreeComputation.compute(0, nodes, graph);   // calcul séquentiel de contrôle
        long sequentialMs = (System.nanoTime() - start) / 1_000_000;
        boolean correct = Arrays.equals(reference.out(), report.out()) && Arrays.equals(reference.in(), report.in());

        int maxNode = 0;
        long degreeSum = 0;
        for (int v = 0; v < nodes; v++) {
            degreeSum += report.degree(v);
            if (report.degree(v) > report.degree(maxNode)) {
                maxNode = v;
            }
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("master", id);
        answer.put("nodes", nodes);
        answer.put("edges", edges);
        answer.put("workers", workerIds);
        answer.put("deadWorkers", report.deadWorkers());
        answer.put("tasks", report.tasks());
        answer.put("retries", report.retries());
        answer.put("tasksComputedByMaster", report.localTasks());
        answer.put("parallelMs", parallelMs);
        answer.put("sequentialMs", sequentialMs);
        answer.put("correct", correct);
        answer.put("degreeSumIsTwiceEdges", degreeSum == 2L * edges);
        answer.put("maxDegreeNode", maxNode);
        answer.put("maxDegree", report.degree(maxNode));
        if (nodes <= 20) {
            List<Long> degrees = new ArrayList<>();
            for (int v = 0; v < nodes; v++) {
                degrees.add(report.degree(v));
            }
            answer.put("degrees", degrees);
        }
        return answer;
    }

    /** "1,2,3" donne [1,2,3] ; absent : tous les autres philosophes. */
    private List<Integer> parseWorkers(String workers) {
        List<Integer> ids = new ArrayList<>();
        if (workers == null || workers.isBlank()) {
            for (int p = 0; p < total; p++) {
                if (p != id) {
                    ids.add(p);
                }
            }
            return ids;
        }
        try {
            for (String s : workers.split(",")) {
                ids.add(Integer.parseInt(s.trim()));
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workers : liste d'entiers séparés par des virgules");
        }
        return ids;
    }
}
