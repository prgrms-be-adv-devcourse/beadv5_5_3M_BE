package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: ticket.reserved
// sender: ticket-service
public record TicketReservedMessage(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {
}
