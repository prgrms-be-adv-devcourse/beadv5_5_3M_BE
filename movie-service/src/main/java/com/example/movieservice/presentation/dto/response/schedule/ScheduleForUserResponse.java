package com.example.movieservice.presentation.dto.response.schedule;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "상영 일정 조회 응답 (사용자용)")
public record ScheduleForUserResponse(
        @Schema(description = "스케줄 ID", example = "1")
        Long scheduleId,

        @Schema(description = "티켓팅 시작 시간", example = "2026-04-01T12:00:00")
        LocalDateTime ticketingTime,

        @Schema(description = "상영 시작 시간", example = "2026-04-01T14:00:00")
        LocalDateTime startTime,

        @Schema(description = "남은 좌석 수", example = "85")
        Integer remainingSeats,

        @Schema(description = "스케줄 상태 (SCHEDULED / WAITING / ON_AIR / COMPLETED)", example = "SCHEDULED")
        String status
) {
}
