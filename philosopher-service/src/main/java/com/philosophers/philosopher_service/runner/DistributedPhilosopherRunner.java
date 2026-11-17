package com.philosophers.philosopher_service.runner;

import com.philosophers.philosopher_service.dto.EventDto;
import com.philosophers.philosopher_service.events.EventPublisher;
import com.philosophers.philosopher_service.protocol.ForkProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Random;

/**
 * Philosophe de la partie 3.3 : aucun superviseur, les fourchettes sont obtenues
 * par le protocole distribué par jetons (ForkProtocol).
 */
@Component
@ConditionalOnProperty(name = "philosopher.mode", havingValue = "distributed", matchIfMissing = true)
public class DistributedPhilosopherRunner implements CommandLineRunner {

    private final ForkProtocol protocol;
    private final EventPublisher events;
    private final Random random = new Random();

    @Value("${philosopher.id:0}")
    private int philosopherId;

    public DistributedPhilosopherRunner(ForkProtocol protocol, EventPublisher events) {
        this.protocol = protocol;
        this.events = events;
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(3000);   // laisse le temps aux autres philosophes de démarrer

        while (true) {
            // 1. Penser
            logState("réfléchit");
            Thread.sleep(random.nextInt(2000) + 1000);

            // 2. Avoir faim, puis obtenir les deux fourchettes via le protocole
            logState("attend (a faim)");
            sendEvent("HUNGRY");
            protocol.acquireForks();

            // 3. Manger
            logState("mange");
            sendEvent("EATING");
            Thread.sleep(random.nextInt(2000) + 1000);
            sendEvent("DONE");
            logState("a fini de manger");

            // 4. Libérer
            protocol.releaseForks();
        }
    }

    private void logState(String state) {
        System.out.println(LocalTime.now() + " Philosophe " + philosopherId + " : " + state);
    }

    private void sendEvent(String state) {
        EventDto event = new EventDto(philosopherId, state, protocol.nextClock());
        events.publish(event);
    }
}
