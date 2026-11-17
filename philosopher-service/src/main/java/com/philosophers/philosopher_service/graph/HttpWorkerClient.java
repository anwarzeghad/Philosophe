package com.philosophers.philosopher_service.graph;

import com.philosophers.philosopher_service.protocol.PeerUrls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Appelle POST /graph/task sur le philosophe worker (port = base-port + id), comme les appels
 * RestTemplate des TP. Le délai d'attente est plus long que celui des messages du protocole
 * des fourchettes car un worker peut avoir un gros lot à traiter.
 */
@Component
public class HttpWorkerClient implements WorkerClient {

    private final RestTemplate restTemplate;
    private final PeerUrls peerUrls;

    public HttpWorkerClient(PeerUrls peerUrls,
                            @Value("${graph.task-timeout-ms:5000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.peerUrls = peerUrls;
    }

    @Override
    public TaskResult execute(int workerId, TaskRequest task) {
        TaskResult result = restTemplate.postForObject(
                peerUrls.of(workerId) + "/graph/task", task, TaskResult.class);
        if (result == null || result.taskId() != task.taskId()) {
            throw new IllegalStateException("réponse invalide du worker " + workerId);
        }
        return result;
    }
}
