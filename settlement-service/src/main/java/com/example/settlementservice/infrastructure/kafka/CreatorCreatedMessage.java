package com.example.settlementservice.infrastructure.kafka;

import java.util.UUID;

// topic: creator.created
// settlement-service는 creatorId만 사용 (nickname 등 나머지 필드는 무시)
public record CreatorCreatedMessage(UUID creatorId) {}