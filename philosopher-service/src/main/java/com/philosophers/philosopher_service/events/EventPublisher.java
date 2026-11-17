package com.philosophers.philosopher_service.events;

import com.philosophers.philosopher_service.dto.EventDto;

/** Envoi d'un événement (HUNGRY, EATING, DONE) au moniteur : par HTTP ou par Kafka. */
public interface EventPublisher {

    /** Ne doit jamais lever d'exception : un moniteur indisponible ne doit pas bloquer un philosophe. */
    void publish(EventDto event);
}
