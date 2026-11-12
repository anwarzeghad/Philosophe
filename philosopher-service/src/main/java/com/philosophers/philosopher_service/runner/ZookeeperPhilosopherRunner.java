package com.philosophers.philosopher_service.runner;

import com.philosophers.philosopher_service.dto.EventDto;
import com.philosophers.philosopher_service.protocol.ForkProtocol;
import com.philosophers.philosopher_service.protocol.ZookeeperForkLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.util.Random;

/**
 * Variante de la partie 3.4 : ZooKeeper coordonne directement l'accès aux
 * fourchettes, sans superviseur ni échange de jetons entre philosophes.
 */
@Component
@ConditionalOnProperty(name = "philosopher.mode", havingValue = "zookeeper")
public class ZookeeperPhilosopherRunner implements CommandLineRunner {

    private final ZookeeperForkLock forkLock;
    private final RestTemplate restTemplate;
    private final ForkProtocol clock;
    private final Random random = new Random();

    @Value("${philosopher.id:0}")
    private int philosopherId;

    public ZookeeperPhilosopherRunner(
            ZookeeperForkLock forkLock, RestTemplate restTemplate, ForkProtocol clock) {
        this.forkLock = forkLock;
        this.restTemplate = restTemplate;
        this.clock = clock;
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(3000);
        while (!Thread.currentThread().isInterrupted()) {
            logState("réfléchit");
            Thread.sleep(random.nextInt(2000) + 1000);

            logState("attend (a faim)");
            sendEvent("HUNGRY");
            if (!forkLock.acquire()) {
                logState("réessaie (verrous indisponibles)");
                continue;
            }

            try {
                logState("mange");
                sendEvent("EATING");
                Thread.sleep(random.nextInt(2000) + 1000);
                sendEvent("DONE");
                logState("a fini de manger");
            } finally {
                forkLock.release();
            }
        }
    }

    private void logState(String state) {
        System.out.println(LocalTime.now() + " Philosophe " + philosopherId + " : " + state);
    }

    private void sendEvent(String state) {
        EventDto event = new EventDto(philosopherId, state, clock.nextClock());
        try {
            restTemplate.postForObject("http://localhost:8080/events", event, Void.class);
        } catch (Exception e) {
            System.err.println("Impossible de contacter le moniteur : " + e.getMessage());
        }
    }
}
