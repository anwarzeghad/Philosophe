package com.philosophers.monitor_service.dto;
// DTO (Data Transfer Object) : classe simple servant uniquement a transporter les donnees
// de l'evenement (format JSON) entre le philosophe et le moniteur via HTTP.
public record EventDto(
    Long philosopherId,
    String state,        // "HUNGRY", "EATING", "DONE"
    long logicalClock    // Horloge logique locale du philosophe
) {}