package com.example.ticketservice.infrastructure.messaging.dto.event;

public record CartClosedMessage(
        Long scheduleId,
        String caseType,
        Integer seats
) {}