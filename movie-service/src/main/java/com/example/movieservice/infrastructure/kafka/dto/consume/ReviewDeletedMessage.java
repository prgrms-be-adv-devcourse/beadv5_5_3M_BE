package com.example.movieservice.infrastructure.kafka.dto.consume;

// topic: review.deleted
// sender: review-service
public record ReviewDeletedMessage(
        Long reviewId
) {
}
