package com.philosophers.philosopher_service.graph;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rôle Worker : n'importe quel philosophe peut calculer les degrés d'un lot d'arêtes.
 * graph.worker.delay-ms permet de simuler un worker lent (tests de résilience).
 */
@RestController
@RequestMapping("/graph")
public class GraphWorkerController {

    @Value("${graph.worker.delay-ms:0}")
    private long delayMs;

    @PostMapping("/task")
    public TaskResult task(@RequestBody TaskRequest task) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        try {
            return DegreeComputation.compute(task.taskId(), task.nodes(), task.edges());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
