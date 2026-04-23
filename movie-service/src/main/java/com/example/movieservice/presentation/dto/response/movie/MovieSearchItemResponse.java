package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영화 검색 결과 항목")
public record MovieSearchItemResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "크리에이터 닉네임", example = "놀란감독")
        String creatorNickname,

        @Schema(description = "하이라이트 처리된 제목 (<em> 태그 포함, 매칭 없으면 null)", example = "<em>인터</em>스텔라")
        String highlightedTitle,

        @Schema(description = "하이라이트 처리된 크리에이터 닉네임", example = "<em>봉준</em>호감독")
        String highlightedCreatorNickname
) {
}
