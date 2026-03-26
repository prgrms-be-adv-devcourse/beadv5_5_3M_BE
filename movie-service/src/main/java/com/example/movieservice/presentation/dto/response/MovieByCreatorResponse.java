package com.example.movieservice.presentation.dto.response;

import java.util.List;

public record MovieByCreatorResponse(
        Long movieId,
        String title,
        Float averageRating,
        List<Long> categoryIds
        // todo: 나중에 이미지도 추가
) {
}
