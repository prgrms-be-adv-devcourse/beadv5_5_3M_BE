package com.example.rivewservice.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Schema(description = "리뷰 수정 요청")
public record UpdateReviewRequest(
        @Schema(description = "수정할 리뷰 내용", example = "다시 보니 더 재미있었습니다", requiredMode = REQUIRED)
        @NotBlank String comment,

        @Schema(description = "수정할 평점 (1~5)", example = "4", minimum = "1", maximum = "5", requiredMode = REQUIRED)
        @NotNull @Min(1) @Max(5) Integer rating
) {
}