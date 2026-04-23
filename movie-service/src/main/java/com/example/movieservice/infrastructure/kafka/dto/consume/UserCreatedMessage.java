package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: user.created
// producer: user-service
public record UserCreatedMessage(
        UUID userId,
        String nickname,
        String profileUrl
) {}