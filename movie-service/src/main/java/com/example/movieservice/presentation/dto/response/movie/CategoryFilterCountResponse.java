package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 필터 카운트 API 응답.
 * terms aggregation으로 각 카테고리별 영화 수를 반환합니다.
 * 프론트엔드에서 "드라마(15) SF(8) 액션(23)" 같은 필터 UI를 그릴 때 사용.
 *
 * 추가로 평점 범위별 영화 수도 포함합니다.
 */
@Schema(description = "필터 카운트 응답 (카테고리별 + 평점 범위별 영화 수)")
public record CategoryFilterCountResponse(
        @Schema(description = "카테고리별 영화 수")
        List<CategoryCount> categories,

        @Schema(description = "평점 범위별 영화 수")
        List<RatingRangeCount> ratingRanges
) {
    @Schema(description = "카테고리별 영화 수")
    public record CategoryCount(
            @Schema(description = "카테고리명", example = "드라마")
            String categoryName,

            @Schema(description = "해당 카테고리의 영화 수", example = "15")
            long count
    ) {}

    @Schema(description = "평점 범위별 영화 수")
    public record RatingRangeCount(
            @Schema(description = "범위 라벨", example = "4.0~5.0")
            String range,

            @Schema(description = "해당 범위의 영화 수", example = "12")
            long count
    ) {}
}
