package com.example.ticketservice.application.event;

import java.time.LocalDateTime;

public record TicketingStartedEvent(
        Long scheduleId,
        long remaining,
        int seats,
        int cookie,
        LocalDateTime startTime
) {}