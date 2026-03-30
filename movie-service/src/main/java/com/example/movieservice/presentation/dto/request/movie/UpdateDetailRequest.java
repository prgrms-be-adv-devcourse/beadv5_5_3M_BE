package com.example.movieservice.presentation.dto.request.movie;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

@Schema(description = "영화 정보 수정 요청")
public record UpdateDetailRequest(
        @Schema(description = "영화 제목", example = "인터스텔라")
        @NotBlank String title,

        @Schema(description = "영화 설명", example = "우주를 배경으로 한 SF 영화입니다.")
        @NotBlank String description,

        @Schema(description = "추가 쿠키 수", example = "5")
        @NotNull @PositiveOrZero Integer additionalCookie,

        @Schema(description = "카테고리 ID 목록 (선택)", example = "[1, 2]")
        List<Long> categoryIds
) {
}
