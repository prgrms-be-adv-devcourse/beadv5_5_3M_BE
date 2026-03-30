package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 영화 목록 응답 (크리에이터용)")
public record MovieForCreatorResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "공개 여부 (PUBLIC / PRIVATE)", example = "PUBLIC")
        String visibility
        // 나중에 이미지도 추가
) {
}
