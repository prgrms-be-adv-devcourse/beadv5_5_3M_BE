package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: ticket.review.authorized
// producer: ticket-service (티켓 구매 후 리뷰 권한 부여)
public record ReviewAuthorizedMessage(
        Long ticketId,
        Long movieId,
        Long scheduleId,
        UUID userId
) {}