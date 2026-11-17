package com.philosophers.monitor_service.controller;

import com.philosophers.monitor_service.dto.EventDto;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * État du moniteur, alimenté par les deux transports : HTTP (MonitorController) et Kafka
 * (KafkaEventConsumer). Chaque événement reçu incrémente aussi la métrique Prometheus
 * philosophers_events_total{state=...}, utile pour superviser le nombre de repas.
 */
@Component
public class EventStore {

    // Historique brut, dans l'ordre d'arrivée au moniteur
    private final List<EventDto> events = new CopyOnWriteArrayList<>();
    // Dernier événement connu de chaque philosophe (plus grande horloge logique locale)
    private final Map<Long, EventDto> lastEvents = new ConcurrentHashMap<>();
    private final MeterRegistry metrics;

    public EventStore(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    public void record(EventDto event) {
        events.add(event);
        lastEvents.merge(event.philosopherId(), event,
                (old, neu) -> neu.logicalClock() >= old.logicalClock() ? neu : old);
        metrics.counter("philosophers.events", "state", String.valueOf(event.state())).increment();
        System.out.println("LOG MONITEUR -> Philosophe " + event.philosopherId()
                + " | État : " + event.state()
                + " | Horloge : " + event.logicalClock());
    }

    /** Événements regroupés par philosophe et ordonnés par horloge logique locale. */
    public Map<Long, List<EventDto>> history() {
        Map<Long, List<EventDto>> result = new TreeMap<>();
        events.stream()
                .sorted(Comparator.comparingLong(EventDto::logicalClock))
                .forEach(e -> result.computeIfAbsent(e.philosopherId(), k -> new java.util.ArrayList<>()).add(e));
        return result;
    }

    public Map<Long, EventDto> currentState() {
        return new TreeMap<>(lastEvents);
    }

    public void reset() {
        events.clear();
        lastEvents.clear();
    }
}
