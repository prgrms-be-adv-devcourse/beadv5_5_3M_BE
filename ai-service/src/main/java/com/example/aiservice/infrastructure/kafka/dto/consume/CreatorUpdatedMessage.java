package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.UUID;

public record CreatorUpdatedMessage(
        UUID creatorId,
        String nickname
) {}