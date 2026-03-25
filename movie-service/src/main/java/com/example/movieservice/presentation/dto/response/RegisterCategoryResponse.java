package com.example.movieservice.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 등록 응답")
public record RegisterCategoryResponse(
        @Schema(description = "생성된 카테고리 ID", example = "1")
        Long categoryId
) {
}
