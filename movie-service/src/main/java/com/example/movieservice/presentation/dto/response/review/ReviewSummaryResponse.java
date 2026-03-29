package com.example.movieservice.presentation.dto.response.review;

import java.time.LocalDateTime;

public record ReviewSummaryResponse(
        Long reviewId,
        String nickname,
        Integer rating,
        String comment,
        LocalDateTime updatedAt,
        String status
) {
}
