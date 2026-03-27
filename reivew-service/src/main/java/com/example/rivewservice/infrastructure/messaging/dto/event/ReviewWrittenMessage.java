package com.example.rivewservice.infrastructure.messaging.dto.event;

// topic: review.written
// groupId: movie-service
public record ReviewWrittenMessage(
        Long reviewId,
        Integer rating,
        String content,
        Long movieId
) {
}
