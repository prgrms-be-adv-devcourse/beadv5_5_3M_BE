package com.example.userservice.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record MovieLikedMessage(
        UUID userId,
        Long movieId,
        String action
) {}
