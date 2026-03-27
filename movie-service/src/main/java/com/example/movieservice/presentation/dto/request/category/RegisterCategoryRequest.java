package com.example.movieservice.presentation.dto.request.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카테고리 등록 요청")
public record RegisterCategoryRequest(
        @Schema(description = "카테고리 이름", example = "SF")
        @NotBlank String name
) {
}

