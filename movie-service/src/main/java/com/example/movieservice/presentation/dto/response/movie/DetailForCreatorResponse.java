package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "영화 상세 조회 응답 (크리에이터용)")
public record DetailForCreatorResponse(
        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "영화 설명", example = "우주를 배경으로 한 SF 영화입니다.")
        String description,

        @Schema(description = "카테고리 ID 목록", example = "[1, 2]")
        List<Long> categoryIds,

        @Schema(description = "기본 쿠키 수", example = "10")
        Integer baseCookie,

        @Schema(description = "추가 쿠키 수", example = "5")
        Integer additionalCookie
        // 여기에 이미지 추가
) {
}
