package com.philosophers.supervisor_service.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/forks")
public class SupervisorController {
    // Map pour stocker l'état des fourchettes (key: idFourchette, value: prise ou pas)
    private final Map<Integer, Boolean> forks = new ConcurrentHashMap<>();
    // Interroger le superviseur : la fourchette est-elle disponible ? (aucune réservation)
    @GetMapping("/{forkId}/available")
    public boolean isAvailable(@PathVariable int forkId) {
        return !forks.getOrDefault(forkId, false);
    }

    // Demander à prendre une fourchette
    @PostMapping("/{forkId}/take")
    public boolean takeFork(@PathVariable int forkId) {
        Boolean isTaken = forks.getOrDefault(forkId, false);
        if (!isTaken) {
            forks.put(forkId, true); // On réserve la fourchette
            System.out.println("Fourchette " + forkId + " attribuée.");
            return true;
        }
        System.out.println("Fourchette " + forkId + " occupée.");
        return false; // Fourchette indisponible
    }

    // Libérer une fourchette
    @PostMapping("/{forkId}/release")
    public void releaseFork(@PathVariable int forkId) {
        forks.put(forkId, false);
        System.out.println("Fourchette " + forkId + " libérée.");
    }
}

