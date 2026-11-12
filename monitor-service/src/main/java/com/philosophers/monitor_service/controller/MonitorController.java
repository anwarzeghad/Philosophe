package com.philosophers.monitor_service.controller;

import com.philosophers.monitor_service.dto.EventDto;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/events")
public class MonitorController {

    // Historique brut, dans l'ordre d'arrivee au moniteur
    private final List<EventDto> events = new CopyOnWriteArrayList<>();
    // Dernier evenement connu de chaque philosophe (plus grande horloge logique locale)
    private final Map<Long, EventDto> lastEvents = new ConcurrentHashMap<>();

    @PostMapping
    public void receiveEvent(@RequestBody EventDto event) {
        events.add(event);
        lastEvents.merge(event.philosopherId(), event,
                (old, neu) -> neu.logicalClock() >= old.logicalClock() ? neu : old);
        System.out.println("LOG MONITEUR -> Philosophe " + event.philosopherId()
            + " | État : " + event.state()
            + " | Horloge : " + event.logicalClock());
    }

    /**
     * Historique reconstruit : evenements regroupes par philosophe et ordonnes
     * par horloge logique locale (l'ordre d'arrivee HTTP n'est pas fiable).
     */
    @GetMapping
    public Map<Long, List<EventDto>> history() {
        Map<Long, List<EventDto>> result = new TreeMap<>();
        events.stream()
                .sorted(Comparator.comparingLong(EventDto::logicalClock))
                .forEach(e -> result.computeIfAbsent(e.philosopherId(), k -> new java.util.ArrayList<>()).add(e));
        return result;
    }

    /** Etat courant de chaque philosophe, pour le tableau de bord. */
    @GetMapping("/state")
    public Map<Long, EventDto> currentState() {
        return new TreeMap<>(lastEvents);
    }

    /** Remet le moniteur a zero (utile pour les tests). */
    @DeleteMapping
    public void reset() {
        events.clear();
        lastEvents.clear();
    }
}
