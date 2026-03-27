package com.example.rivewservice.infrastructure.messaging.dto.event;

import java.util.UUID;

// topic: review.written
// groupId: movie-service
public record ReviewWrittenMessage(
        Long reviewId,
        UUID userId,
        String nickname,
        Integer rating,
        String content,
        Long movieId
) {
}
