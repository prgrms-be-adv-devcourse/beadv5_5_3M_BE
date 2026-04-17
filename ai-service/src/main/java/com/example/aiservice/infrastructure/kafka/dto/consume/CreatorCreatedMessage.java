package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record CreatorCreatedMessage(
        UUID creatorId,
        String nickname
) {}
