package com.philosophers.philosopher_service.graph;

/** Calcul des degrés sur un lot d'arêtes. Utilisé par les workers, par le repli local du master et par la vérification. */
public final class DegreeComputation {

    private DegreeComputation() {
    }

    public static TaskResult compute(int taskId, int nodes, int[] edges) {
        if (nodes < 0 || edges.length % 2 != 0) {
            throw new IllegalArgumentException("tâche invalide");
        }
        int[] out = new int[nodes];
        int[] in = new int[nodes];
        for (int i = 0; i < edges.length; i += 2) {
            int u = edges[i];
            int v = edges[i + 1];
            if (u < 0 || u >= nodes || v < 0 || v >= nodes) {
                throw new IllegalArgumentException("nœud hors bornes dans l'arête (" + u + "," + v + ")");
            }
            out[u]++;
            in[v]++;
        }
        return new TaskResult(taskId, out, in);
    }
}
