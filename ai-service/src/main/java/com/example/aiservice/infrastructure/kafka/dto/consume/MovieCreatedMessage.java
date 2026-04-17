package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record MovieCreatedMessage(
        Long movieId,
        String title,
        String[] category,
        String imageUrl,
        UUID creatorId,
        String description
) {
}
