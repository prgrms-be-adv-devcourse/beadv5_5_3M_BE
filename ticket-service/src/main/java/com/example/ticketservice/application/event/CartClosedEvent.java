package com.example.ticketservice.application.event;

public record CartClosedEvent(
        Long scheduleId,
        String caseType,
        Integer seats
) {}