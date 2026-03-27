package com.example.movieservice.presentation.dto.response;

public record MovieForCreatorResponse(
        Long movieId,
        String title,
        String visibility
        // 나중에 이미지도 추가
) {
}
