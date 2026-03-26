package com.example.movieservice.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "영화 공개 여부 변경 요청")
public record UpdateVisibilityRequest(
        @Schema(description = "공개 여부 (PUBLIC / PRIVATE)", example = "PUBLIC")
        @NotNull Visibility visibility
) {
        public enum Visibility { PUBLIC, PRIVATE }
}
