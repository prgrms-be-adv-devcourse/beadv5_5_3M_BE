package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: review.written
// sender: review-service
public record ReviewWrittenMessage(
        Long reviewId,
        UUID userId,
        String nickname,
        Integer rating,
        String content,
        Long movieId
) {
}
