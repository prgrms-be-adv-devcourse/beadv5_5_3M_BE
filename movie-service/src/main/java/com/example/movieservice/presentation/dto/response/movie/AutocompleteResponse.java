package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 자동완성 API 응답.
 * 사용자가 입력 중인 키워드에 대해 매칭되는 영화 제목 목록을 반환합니다.
 *
 * 예시: prefix="인터스" → suggestions=["인터스텔라", "인터스텔라 다큐멘터리"]
 */
@Schema(description = "자동완성 응답")
public record AutocompleteResponse(
        @Schema(description = "자동완성 제안 목록")
        List<AutocompleteItem> suggestions
) {
    @Schema(description = "자동완성 항목")
    public record AutocompleteItem(
            @Schema(description = "영화 ID", example = "42")
            Long movieId,

            @Schema(description = "영화 제목", example = "인터스텔라")
            String title,

            @Schema(description = "크리에이터 닉네임", example = "놀란")
            String creatorNickname
    ) {}
}
