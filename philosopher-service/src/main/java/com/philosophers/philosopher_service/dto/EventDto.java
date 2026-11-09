package com.philosophers.philosopher_service.dto;

public record EventDto(
    long philosopherId,
    String state,
    long logicalClock
) {} 