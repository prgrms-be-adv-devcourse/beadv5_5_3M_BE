package com.example.aiservice.infrastructure.kafka.dto.consume;

import java.util.List;

public record MovieUpdatedMessage(
        Long movieId,
        List<String> changedFields,
        String description,
        String[] category,
        String visibility
) {
}
