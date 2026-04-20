package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.List;

// topic: movie.updated
// producer: creator-service (PUBLIC 상태에서 상세 수정 시)
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