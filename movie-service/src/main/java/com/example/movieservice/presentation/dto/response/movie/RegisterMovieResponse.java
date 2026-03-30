package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영화 등록 응답")
public record RegisterMovieResponse(
        @Schema(description = "생성된 영화 ID", example = "1")
        Long movieId
) {
}
