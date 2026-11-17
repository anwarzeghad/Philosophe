package com.philosophers.monitor_service.controller;

import com.philosophers.monitor_service.dto.EventDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
public class MonitorController {

    private final EventStore store;

    public MonitorController(EventStore store) {
        this.store = store;
    }

    @PostMapping
    public void receiveEvent(@RequestBody EventDto event) {
        store.record(event);
    }

    /**
     * Historique reconstruit : evenements regroupes par philosophe et ordonnes
     * par horloge logique locale (l'ordre d'arrivee HTTP n'est pas fiable).
     */
    @GetMapping
    public Map<Long, List<EventDto>> history() {
        return store.history();
    }

    /** Etat courant de chaque philosophe, pour le tableau de bord. */
    @GetMapping("/state")
    public Map<Long, EventDto> currentState() {
        return store.currentState();
    }

    /** Remet le moniteur a zero (utile pour les tests). */
    @DeleteMapping
    public void reset() {
        store.reset();
    }
}
