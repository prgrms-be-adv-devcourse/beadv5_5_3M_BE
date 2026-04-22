package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: user.updated
// producer: user-service
public record UserUpdatedMessage(
        UUID userId,
        String nickname,
        String profileUrl
) {}