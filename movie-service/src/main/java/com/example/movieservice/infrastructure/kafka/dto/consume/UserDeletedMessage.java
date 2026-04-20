package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

// topic: user.deleted
// producer: user-service
public record UserDeletedMessage(
        UUID userId
) {}