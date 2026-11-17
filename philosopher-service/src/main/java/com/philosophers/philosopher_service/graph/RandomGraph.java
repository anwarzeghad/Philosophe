package com.philosophers.philosopher_service.graph;

import java.util.Random;

/** Génère un grand graphe orienté aléatoire (reproductible avec la graine), arêtes à plat [u0, v0, u1, v1, ...]. */
public final class RandomGraph {

    private RandomGraph() {
    }

    public static int[] edges(int nodes, int edgeCount, long seed) {
        Random random = new Random(seed);
        int[] edges = new int[2 * edgeCount];
        for (int i = 0; i < edgeCount; i++) {
            edges[2 * i] = random.nextInt(nodes);
            edges[2 * i + 1] = random.nextInt(nodes);
        }
        return edges;
    }
}
