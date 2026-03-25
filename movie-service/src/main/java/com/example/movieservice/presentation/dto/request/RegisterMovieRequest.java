package com.example.movieservice.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

@Schema(description = "영화 등록 요청")
public record RegisterMovieRequest(
        @Schema(description = "영화 제목", example = "인터스텔라")
        @NotBlank String title,

        @Schema(description = "영화 설명", example = "우주를 배경으로 한 SF 영화입니다.")
        @NotBlank String description,

        @Schema(description = "상영 시간 (초 단위)", example = "9720")
        @NotNull @Positive Integer runningTime,

        @Schema(description = "기본 쿠키 수", example = "10")
        @NotNull @PositiveOrZero Integer baseCookie,

        @Schema(description = "추가 쿠키 수", example = "5")
        @NotNull @PositiveOrZero Integer additionalCookie,

        @Schema(description = "카테고리 ID 목록 (선택)", example = "[1, 2]")
        List<Long> categoryIds
) {
}
