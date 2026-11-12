package com.philosophers.philosopher_service.dto;

// Transfert du jeton d'une fourchette d'un philosophe à un autre
public record TokenMessage(int fork, int fromId, long timestamp) {}
