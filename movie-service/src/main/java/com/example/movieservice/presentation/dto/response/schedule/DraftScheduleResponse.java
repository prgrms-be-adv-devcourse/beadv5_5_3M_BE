package com.example.movieservice.presentation.dto.response.schedule;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "편성 목록 조회 응답 (크리에이터용)")
public record DraftScheduleResponse(
        @Schema(description = "스케줄 ID", example = "1")
        Long scheduleId,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "상영 시작 시간", example = "2026-04-01T14:00:00")
        LocalDateTime startTime,

        @Schema(description = "상영 종료 시간", example = "2026-04-01T16:42:00")
        LocalDateTime endTime,

        @Schema(description = "확정 여부", example = "false")
        Boolean isConfirmed,

        @Schema(description = "스케줄 상태 (SCHEDULED / WAITING / ON_AIR / COMPLETED)", example = "SCHEDULED")
        String status
) {}
