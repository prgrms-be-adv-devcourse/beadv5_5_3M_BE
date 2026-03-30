package com.example.movieservice.presentation.dto.request.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "상영 일정 확정 요청")
public record UpdateConfirmRequest(
        @Schema(description = "확정할 스케줄 ID", example = "1")
        @NotNull Long scheduleId
) {
}
