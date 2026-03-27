package com.example.rivewservice.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Schema(description = "리뷰 작성 요청")
public record WriteReviewRequest(
        @Schema(description = "영화 ID", example = "1", requiredMode = REQUIRED)
        @NotNull Long movieId,

        @Schema(description = "리뷰 내용", example = "재미있었습니다", requiredMode = REQUIRED)
        @NotBlank String comment,

        @Schema(description = "평점 (1~5)", example = "5", minimum = "1", maximum = "5", requiredMode = REQUIRED)
        @NotNull @Min(1) @Max(5) Integer rating
) {
}