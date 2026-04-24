package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record UserDeletedMessage(
        UUID userId
) {}
