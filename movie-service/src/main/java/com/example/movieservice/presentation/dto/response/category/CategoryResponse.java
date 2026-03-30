package com.example.movieservice.presentation.dto.response.category;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 조회 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "카테고리 이름", example = "SF")
        String name
) {
}
