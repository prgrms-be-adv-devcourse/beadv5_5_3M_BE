package com.example.streamingservice.infrastructure.messaging.dto;

import java.util.UUID;

public record TicketReviewAuthorizedPayload(
		Long ticketId,
		Long movieId,
		Long scheduleId,
		UUID userId
) {
}