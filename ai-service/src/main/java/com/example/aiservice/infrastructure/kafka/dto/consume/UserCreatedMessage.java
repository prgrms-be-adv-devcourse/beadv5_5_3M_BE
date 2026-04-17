package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record UserCreatedMessage(
        UUID userId,
        String nickname,
        String profileUrl,
        int ageGroup,
        String gender
) {}
