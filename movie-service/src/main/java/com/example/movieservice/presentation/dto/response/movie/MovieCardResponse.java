package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "영화 카드 응답")
public record MovieCardResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "크리에이터 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID creatorId,

        @Schema(description = "크리에이터 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,
//        String imageUrl,
        @Schema(description = "평균 평점", example = "4.5")
        Float averageRating,

        @Schema(description = "카테고리 ID 목록", example = "[1, 2]")
        List<Long> categoryIds
) {
}
