package com.example.movieservice.presentation.dto.response.schedule;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "확정 일정 조회 응답 (크리에이터용)")
public record ScheduleForCreatorResponse(
        @Schema(description = "스케줄 ID", example = "1")
        Long scheduleId,

        @Schema(description = "영화 제목", example = "인터스텔라")
        String title,

        @Schema(description = "상영 시작 시간", example = "2026-04-01T14:00:00")
        LocalDateTime startTime,

        @Schema(description = "상영 종료 시간", example = "2026-04-01T16:42:00")
        LocalDateTime endTime,

        @Schema(description = "총 좌석 수", example = "100")
        Integer totalSeats,

        @Schema(description = "남은 좌석 수", example = "85")
        Integer remainingSeats
        // todo : 나중에 이미지도 추가해야 함
) {
}
