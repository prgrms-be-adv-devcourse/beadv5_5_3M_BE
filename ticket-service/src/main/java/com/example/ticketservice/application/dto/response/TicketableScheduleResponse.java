package com.example.ticketservice.application.dto.response;

import com.example.ticketservice.domain.enums.SchedulePhase;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "티켓팅 진입 가능한 스케줄(회차) 응답")
public record TicketableScheduleResponse(
        @Schema(description = "스케줄 ID") Long scheduleId,
        @Schema(description = "영화 ID") Long movieId,
        @Schema(description = "크리에이터 ID") UUID creatorId,
        @Schema(description = "공연 제목") String title,
        @Schema(description = "포스터 이미지 URL") String imageUrl,
        @Schema(description = "공연 시작 시간") LocalDateTime startTime,
        @Schema(description = "공연 종료 시간") LocalDateTime endTime,
        @Schema(description = "티켓팅 시작 시간") LocalDateTime ticketingTime,
        @Schema(description = "티켓 가격(쿠키)") Integer cookie,
        @Schema(description = "내부 스케줄 상태") ScheduleStatus status,
        @Schema(description = "사용자 관점 단계") SchedulePhase phase,
        @Schema(description = "총 좌석 수") Integer totalSeats,
        @Schema(description = "잔여 좌석 (TICKETING 단계에서만 채워짐, 그 외 null)") Integer availableSeats
) {
    public static TicketableScheduleResponse from(Schedule schedule, Integer availableSeats) {
        return new TicketableScheduleResponse(
                schedule.getId(),
                schedule.getMovieId(),
                schedule.getCreatorId(),
                schedule.getTitle(),
                schedule.getImageUrl(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getTicketingTime(),
                schedule.getCookie(),
                schedule.getStatus(),
                SchedulePhase.from(schedule.getStatus()),
                schedule.getSeats(),
                availableSeats
        );
    }
}