package com.philosophers.philosopher_service.runner;

import com.philosophers.philosopher_service.dto.EventDto;
import com.philosophers.philosopher_service.events.EventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Random;

// Version avec superviseur (partie 3.2) : active avec --philosopher.mode=supervisor
// (par défaut, c'est DistributedPhilosopherRunner de la partie 3.3 qui tourne)
@Component
@ConditionalOnProperty(name = "philosopher.mode", havingValue = "supervisor")
public class PhilosopherRunner implements CommandLineRunner {

    private final RestTemplate restTemplate;
    private final EventPublisher events;
    private final Random random = new Random();

    private long logicalClock = 0;

    @Value("${philosopher.id:0}")
    private int philosopherId;

    @Value("${philosophers.total:5}")
    private int totalPhilosophers;

    @Value("${supervisor.url:http://localhost:8082}")
    private String supervisorUrl;

    public PhilosopherRunner(RestTemplate restTemplate, EventPublisher events) {
        this.restTemplate = restTemplate;
        this.events = events;
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(3000);

        int leftFork = philosopherId;
        int rightFork = (philosopherId + 1) % totalPhilosophers;

        while (true) {
            // 1. Penser
            logState("réfléchit");
            Thread.sleep(random.nextInt(2000) + 1000);

            // 2. Avoir faim
            logState("attend (a faim)");
            sendEvent("HUNGRY");

            // 3. Demander les deux fourchettes en une seule requête
            if (requestBothForks(leftFork, rightFork)) {
                // 4. Manger
                logState("mange");
                sendEvent("EATING");
                Thread.sleep(random.nextInt(2000) + 1000);
                sendEvent("DONE");
                logState("a fini de manger");

                // 5. Libérer les deux fourchettes
                releaseFork(rightFork);
                releaseFork(leftFork);
            } else {
                // Refus : attendre un peu avant de réessayer
                Thread.sleep(random.nextInt(500) + 200);
            }
        }
    }

    // Partie 3.1 : les deux fourchettes sont demandées ensemble au superviseur
    private boolean requestBothForks(int leftFork, int rightFork) {
        try {
            return Boolean.TRUE.equals(
                restTemplate.postForObject(
                    supervisorUrl + "/forks/take?left=" + leftFork + "&right=" + rightFork,
                    null,
                    Boolean.class)
            );
        } catch (Exception e) {
            return false;
        }
    }

    // Affiche l'état du philosophe dans sa propre console
    private void logState(String state) {
        System.out.println("Philosophe " + philosopherId + " : " + state);
    }

    private void releaseFork(int forkId) {
        try {
            restTemplate.postForObject(
                supervisorUrl + "/forks/" + forkId + "/release",
                null,
                Void.class);
        } catch (Exception e) {
            System.err.println("Erreur libération fourchette : " + e.getMessage());
        }
    }

    private void sendEvent(String state) {
        logicalClock++;
        EventDto event = new EventDto(philosopherId, state, logicalClock);

        events.publish(event);
    }
}
