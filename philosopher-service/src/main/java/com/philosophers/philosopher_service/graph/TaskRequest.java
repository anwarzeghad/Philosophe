package com.philosophers.philosopher_service.graph;

/**
 * Tâche envoyée à un worker : un lot d'arêtes du graphe.
 *
 * @param taskId identifiant de la tâche (sert à ignorer les résultats en double)
 * @param nodes  nombre de nœuds du graphe (les nœuds sont numérotés 0..nodes-1)
 * @param edges  arêtes à plat : [u0, v0, u1, v1, ...] avec l'arête i = (edges[2i] -> edges[2i+1])
 */
public record TaskRequest(int taskId, int nodes, int[] edges) {}
