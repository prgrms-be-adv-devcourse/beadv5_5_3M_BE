package com.example.movieservice.presentation.dto.response;

public record MovieForCreatorResponse(
        Long movieId,
        String title,
        Visibility visibility
        // 나중에 이미지도 추가
) {
    public enum Visibility { PUBLIC, PRIVATE }
}
