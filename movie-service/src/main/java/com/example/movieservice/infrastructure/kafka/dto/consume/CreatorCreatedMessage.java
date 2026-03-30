package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: creator.created
// sender: creator-service
public record CreatorCreatedMessage(
        UUID creatorId,
        String nickname
) {
}
