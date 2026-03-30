package com.example.movieservice.presentation.dto.request.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "상영 일정 등록 요청")
public record RegisterScheduleRequest(
        @Schema(description = "상영할 영화 ID", example = "1")
        @NotNull Long movieId,

        @Schema(description = "상영 시작 시간 (정각이어야 함)", example = "2026-04-01T14:00:00")
        @NotNull LocalDateTime startTime,

        @Schema(description = "상영 종료 시간", example = "2026-04-01T16:42:00")
        @NotNull LocalDateTime endTime
) {
}
