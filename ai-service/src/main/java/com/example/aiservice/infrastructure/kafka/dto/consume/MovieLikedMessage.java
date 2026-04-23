package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record MovieLikedMessage(
        UUID userId,
        Long movieId,
        String action  // "LIKED" | "UNLIKED"
) {
}
