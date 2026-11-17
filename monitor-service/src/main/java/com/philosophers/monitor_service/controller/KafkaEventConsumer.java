package com.philosophers.monitor_service.controller;

import com.philosophers.monitor_service.dto.EventDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consomme les événements des philosophes publiés dans Kafka (partie 6).
 * Les offsets sont validés par Kafka : si le moniteur est arrêté puis redémarré, il reprend
 * les événements produits pendant son absence (groupe de consommateurs "monitor").
 */
@Component
@ConditionalOnProperty(name = "monitor.kafka.enabled", havingValue = "true")
public class KafkaEventConsumer {

    private final EventStore store;

    public KafkaEventConsumer(EventStore store) {
        this.store = store;
    }

    @KafkaListener(topics = "${monitor.topic:philosophers-events}")
    public void onEvent(EventDto event) {
        store.record(event);
    }
}
