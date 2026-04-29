package com.example.movieservice.infrastructure.kafka.dto.consume;

import java.util.List;
import java.util.UUID;

// topic: movie.uploaded
// producer: creator-service (최초 PUBLIC 전환 시 1회 발행)
public record MovieUploadedMessage(
        Long movieId,
        String title,
        String description,
        UUID creatorId,
        String creatorNickname,
        String imgUrl,
        Float averageRating,
        Integer likeCount,
        Integer reviewCount,
        List<CategoryInfo> categories
) {
    public record CategoryInfo(
            Long categoryId,
            String name
    ) {}
}