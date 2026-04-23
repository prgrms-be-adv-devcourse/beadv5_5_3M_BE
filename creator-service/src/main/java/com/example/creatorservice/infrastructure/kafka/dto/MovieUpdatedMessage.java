package com.example.creatorservice.infrastructure.kafka.dto;

import java.util.List;

// topic: movie.updated
// receiver: movie-service (ES 업데이트)
public record MovieUpdatedMessage(
        Long movieId,
        String title,
        String description,
        List<CategoryInfo> categories
) {
    public record CategoryInfo(
            Long categoryId,
            String name
    ) {}
}