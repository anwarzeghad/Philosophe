package com.philosophers.philosopher_service.dto;

// Annonce qu'une demande est terminée (le demandeur a fini de manger)
public record FinishedMessage(int requesterId, long timestamp) {}
