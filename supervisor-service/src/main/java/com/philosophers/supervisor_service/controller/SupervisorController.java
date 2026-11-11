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

    // Partie 3.1 (SANS verrou) : demander les deux fourchettes en une seule requête.
    // Étapes : vérifier F_i et F_{i+1}, puis les réserver toutes les deux, sinon refuser.
    // Cette section critique n'est PAS protégée : deux threads peuvent passer la
    // vérification en même temps et réserver les mêmes fourchettes (corrigé en 3.2).
    @PostMapping("/take")
    public boolean takeBothForks(@RequestParam int left, @RequestParam int right) {
        boolean leftFree = !forks.getOrDefault(left, false);
        boolean rightFree = !forks.getOrDefault(right, false);
        if (leftFree && rightFree) {
            forks.put(left, true);
            forks.put(right, true);
            System.out.println("Fourchettes " + left + " et " + right + " attribuées.");
            return true;
        }
        System.out.println("Fourchettes " + left + " et " + right + " refusées.");
        return false;
    }

    // Libérer une fourchette
    @PostMapping("/{forkId}/release")
    public void releaseFork(@PathVariable int forkId) {
        forks.put(forkId, false);
        System.out.println("Fourchette " + forkId + " libérée.");
    }
}

