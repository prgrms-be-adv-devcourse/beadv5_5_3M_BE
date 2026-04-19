package com.example.aiservice.infrastructure.kafka.dto.consume;

public record MovieCreatedMessage(
        Long movieId,
        String[] category,
        String description
) {
}
