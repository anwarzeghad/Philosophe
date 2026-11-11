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

    // Verrou protégeant la section critique : l'état des fourchettes est modifié
    // par un seul thread à la fois (partie 3.2).
    private final Object lock = new Object();

    // Partie 3.2 (AVEC verrou) : demander les deux fourchettes en une seule requête.
    // Étapes : vérifier F_i et F_{i+1}, puis les réserver toutes les deux, sinon refuser.
    // Le bloc synchronized rend ces étapes atomiques : deux demandes simultanées
    // sont traitées l'une après l'autre.
    @PostMapping("/take")
    public boolean takeBothForks(@RequestParam int left, @RequestParam int right) {
        synchronized (lock) {
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
    }

    // Libérer une fourchette (même verrou que la prise)
    @PostMapping("/{forkId}/release")
    public void releaseFork(@PathVariable int forkId) {
        synchronized (lock) {
            forks.put(forkId, false);
            System.out.println("Fourchette " + forkId + " libérée.");
        }
    }
}

