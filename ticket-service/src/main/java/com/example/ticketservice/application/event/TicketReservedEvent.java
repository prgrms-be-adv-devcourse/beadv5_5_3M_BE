package com.example.ticketservice.application.event;

import java.util.UUID;

public record TicketReservedEvent(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookie
) {}