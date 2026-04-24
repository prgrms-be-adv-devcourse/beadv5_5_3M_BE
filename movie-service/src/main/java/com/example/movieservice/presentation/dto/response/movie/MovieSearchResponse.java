package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "영화 검색 응답")
public record MovieSearchResponse(
        @Schema(description = "총 검색 결과 수", example = "42")
        long total,

        @Schema(description = "검색 결과 항목 목록")
        List<MovieSearchItemResponse> items
) {
}
