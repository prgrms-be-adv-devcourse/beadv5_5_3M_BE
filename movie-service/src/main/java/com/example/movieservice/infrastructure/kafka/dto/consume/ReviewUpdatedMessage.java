package com.example.movieservice.infrastructure.kafka.dto.consume;

// topic: review.updated
// sender: review-service
public record ReviewUpdatedMessage(
        Long reviewId,
        Integer rating,
        String content
) {
}
