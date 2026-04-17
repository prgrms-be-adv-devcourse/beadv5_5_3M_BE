package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record MovieUnlikedMessage(
        UUID userId,
        Long movieId
) {
}
