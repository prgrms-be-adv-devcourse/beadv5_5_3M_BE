package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record UserCreatedMessage(
        UUID userId,
        int ageGroup,
        String gender
) {}
