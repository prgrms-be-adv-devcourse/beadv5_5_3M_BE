package com.example.movieservice.presentation.dto.response.movie;

import java.util.List;

public record MovieByCreatorResponse(
        Long movieId,
        String title,
        Float averageRating,
        List<Long> categoryIds
        // 나중에 이미지도 추가
) {
}
