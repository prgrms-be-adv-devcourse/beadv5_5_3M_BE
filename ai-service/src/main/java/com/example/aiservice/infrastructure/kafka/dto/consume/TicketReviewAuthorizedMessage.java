package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record TicketReviewAuthorizedMessage(
        Long ticketId,
        Long movieId,
        Long scheduleId,
        UUID userId
) {
}
