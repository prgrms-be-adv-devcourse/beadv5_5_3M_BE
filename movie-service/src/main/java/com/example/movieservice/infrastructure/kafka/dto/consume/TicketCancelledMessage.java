package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: ticket.cancelled
// sender: ticket-service
public record TicketCancelledMessage(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {
}
