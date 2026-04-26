package com.example.ticketservice.application.dto.response;

import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "영화 상세 컨텍스트의 회차 응답 (movieId/title/imageUrl/creatorId 등은 영화 상세에서 이미 알고 있어 제외)")
public record MovieScheduleResponse(
        @Schema(description = "스케줄 ID") Long scheduleId,
        @Schema(description = "공연 시작 시간") LocalDateTime startTime,
        @Schema(description = "공연 종료 시간") LocalDateTime endTime,
        @Schema(description = "티켓팅 시작 시간") LocalDateTime ticketingTime,
        @Schema(description = "내부 스케줄 상태 (CART/IN_PROGRESSING/TICKETING/STREAMING)") ScheduleStatus status,
        @Schema(description = "총 좌석 수") Integer totalSeats,
        @Schema(description = "잔여 좌석 (TICKETING 단계에서만 채워짐, 그 외 null)") Integer availableSeats
) {
    public static MovieScheduleResponse from(Schedule schedule, Integer availableSeats) {
        return new MovieScheduleResponse(
                schedule.getId(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getTicketingTime(),
                schedule.getStatus(),
                schedule.getSeats(),
                availableSeats
        );
    }
}