package com.example.rivewservice.infrastructure.messaging.dto.event;

// topic: review.updated
// groupId: movie-service
public record ReviewUpdatedMessage(
    Long reviewId,
    Integer rating,
    String content
) {
}
