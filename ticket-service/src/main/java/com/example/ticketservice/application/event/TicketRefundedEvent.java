package com.example.ticketservice.application.event;

import java.util.UUID;

public record TicketRefundedEvent(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookie
) {}