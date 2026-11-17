package com.philosophers.philosopher_service.graph;

/**
 * Résultat partiel d'un worker, pour les arêtes de sa tâche seulement.
 *
 * @param out out[v] = nombre d'arêtes de la tâche qui partent de v
 * @param in  in[v]  = nombre d'arêtes de la tâche qui arrivent en v
 */
public record TaskResult(int taskId, int[] out, int[] in) {}
