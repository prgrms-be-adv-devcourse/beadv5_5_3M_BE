package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "크리에이터별 영화 목록 응답")
public record MovieByCreatorResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "평균 평점", example = "4.5")
        Float averageRating,

        @Schema(description = "카테고리 ID 목록", example = "[1, 2]")
        List<Long> categoryIds,

        @Schema(description = "포스터 이미지 URL", example = "https://example.com/image.jpg")
        String imageUrl
) {
}
