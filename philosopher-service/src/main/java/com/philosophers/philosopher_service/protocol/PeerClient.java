package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Envoie les messages du protocole aux autres philosophes par HTTP.
 * Le philosophe j écoute sur le port (base-port + j).
 * Chaque envoi a un délai d'attente (configuré dans AppConfig) et est retenté en cas d'échec.
 */
@Component
public class PeerClient implements PeerSender {

    private static final int MAX_ATTEMPTS = 3;
    private static final long PAUSE_BETWEEN_ATTEMPTS_MS = 300;

    private final RestTemplate restTemplate;

    @Value("${philosophers.base-port:9000}")
    private int basePort;

    public PeerClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void sendRequest(int peerId, RequestMessage message) {
        post(peerId, "/protocol/request", message);
    }

    @Override
    public void sendFinished(int peerId, FinishedMessage message) {
        post(peerId, "/protocol/finished", message);
    }

    @Override
    public boolean sendToken(int peerId, TokenMessage message) {
        return post(peerId, "/protocol/token", message);
    }

    private boolean post(int peerId, String path, Object body) {
        String url = "http://localhost:" + (basePort + peerId) + path;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restTemplate.postForObject(url, body, Void.class);
                return true;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(PAUSE_BETWEEN_ATTEMPTS_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        System.err.println("Échec d'envoi vers le philosophe " + peerId + " (" + path + ")");
        return false;
    }
}
