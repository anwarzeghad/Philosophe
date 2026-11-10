package com.philosophers.philosopher_service.runner;

import com.philosophers.philosopher_service.dto.EventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Random;

@Component
public class PhilosopherRunner implements CommandLineRunner {

    private final RestClient restClient;
    private final Random random = new Random();

    private long logicalClock = 0;

    @Value("${philosopher.id:0}")
    private int philosopherId;

    @Value("${philosophers.total:5}")
    private int totalPhilosophers;

    public PhilosopherRunner(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(3000);

        int leftFork = philosopherId;
        int rightFork = (philosopherId + 1) % totalPhilosophers;

        while (true) {
            // 1. Penser
            Thread.sleep(random.nextInt(2000) + 1000);

            // 2. Avoir faim
            sendEvent("HUNGRY");

            // 3. Demander la 1ère fourchette (gauche)
            boolean hasLeft = requestFork(leftFork);
            if (hasLeft) {
                // Demander la 2ème fourchette (droite)
                boolean hasRight = requestFork(rightFork);
                if (hasRight) {
                    // 4. Manger
                    sendEvent("EATING");
                    Thread.sleep(random.nextInt(2000) + 1000);
                    sendEvent("DONE");

                    // Libérer la fourchette droite
                    releaseFork(rightFork);
                }
                // Libérer la fourchette gauche
                releaseFork(leftFork);
            }
        }
    }

    private boolean isForkAvailable(int forkId) {
        try {
            return Boolean.TRUE.equals(
                restClient.get()
                    .uri("http://localhost:8082/forks/" + forkId + "/available")
                    .retrieve()
                    .body(Boolean.class)
            );
        } catch (Exception e) {
            return false;
        }
    }

    // 1) interroger le superviseur, 2) si disponible, demander la fourchette
    private boolean requestFork(int forkId) {
        if (!isForkAvailable(forkId)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                restClient.post()
                    .uri("http://localhost:8082/forks/" + forkId + "/take")
                    .retrieve()
                    .body(Boolean.class)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseFork(int forkId) {
        try {
            restClient.post()
                .uri("http://localhost:8082/forks/" + forkId + "/release")
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Erreur libération fourchette : " + e.getMessage());
        }
    }

    private void sendEvent(String state) {
        logicalClock++;
        EventDto event = new EventDto(philosopherId, state, logicalClock);

        try {
            restClient.post()
                    .uri("http://localhost:8080/events")
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Impossible de contacter le moniteur : " + e.getMessage());
        }
    }
}