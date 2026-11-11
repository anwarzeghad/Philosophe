package com.philosophers.philosopher_service.dto;

// Demande de fourchettes diffusée à tous les philosophes (identifiant + horodatage logique)
public record RequestMessage(int requesterId, long timestamp) {}
