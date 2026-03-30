package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: creator.updated
// sender: creator-service
public record CreatorUpdatedMessage(
        UUID creatorId,
        String nickname
) {
}
