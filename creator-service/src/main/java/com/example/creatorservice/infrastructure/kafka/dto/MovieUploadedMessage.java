package com.example.creatorservice.infrastructure.kafka.dto;

import java.util.List;
import java.util.UUID;

// topic: movie.uploaded
// receiver: movie-service (ES 색인)
public record MovieUploadedMessage(
        Long movieId,
        String title,
        String description,
        UUID creatorId,
        String creatorNickname,
        String imgUrl,
        Float averageRating,
        List<CategoryInfo> categories
) {
    public record CategoryInfo(
            Long categoryId,
            String name
    ) {}
}