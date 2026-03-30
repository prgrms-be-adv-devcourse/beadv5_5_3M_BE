package com.example.movieservice.presentation.dto.response.movie;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "편성 가능한 영화 응답")
public record MovieForScheduleResponse(
        @Schema(description = "영화 ID", example = "1")
        Long movieId,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "상영 시간 (초 단위)", example = "9720")
        Integer runningTime
) {
}
