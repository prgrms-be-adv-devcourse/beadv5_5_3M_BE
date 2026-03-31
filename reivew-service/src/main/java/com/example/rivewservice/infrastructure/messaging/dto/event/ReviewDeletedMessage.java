package com.example.rivewservice.infrastructure.messaging.dto.event;

// topic: review.deleted
// groupId: movie-service
public record ReviewDeletedMessage(
        Long reviewId
) {
}
