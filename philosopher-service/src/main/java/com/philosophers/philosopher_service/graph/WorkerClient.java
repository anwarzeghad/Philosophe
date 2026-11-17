package com.philosophers.philosopher_service.graph;

/** Envoi d'une tâche à un worker (HTTP en production, simulé dans les tests unitaires). */
public interface WorkerClient {

    /**
     * @return le résultat du worker
     * @throws RuntimeException si le worker est injoignable, trop lent ou répond une erreur
     */
    TaskResult execute(int workerId, TaskRequest task);
}
