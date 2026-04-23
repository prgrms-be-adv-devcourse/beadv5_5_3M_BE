package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 인기 검색어 API 응답.
 * terms aggregation 결과로 가장 많이 검색된 키워드 순위를 반환합니다.
 *
 * 예시: keywords=[{keyword:"인터스텔라", count:128}, {keyword:"어벤져스", count:95}]
 */
@Schema(description = "인기 검색어 응답")
public record PopularKeywordResponse(
        @Schema(description = "인기 검색어 목록 (검색 횟수 내림차순)")
        List<KeywordCount> keywords
) {
    @Schema(description = "검색어별 검색 횟수")
    public record KeywordCount(
            @Schema(description = "검색어", example = "인터스텔라")
            String keyword,

            @Schema(description = "검색 횟수", example = "128")
            long count
    ) {}
}
