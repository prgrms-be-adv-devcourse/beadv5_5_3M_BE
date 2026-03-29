package com.example.movieservice.presentation.dto.response.movie;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MovieCardResponse(
        Long movieId,
        UUID creatorId,
        String title,
//        String imageUrl,
        Float averageRating,
        List<Long> categoryIds
) {
}
