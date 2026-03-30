package com.example.ticketservice.application.dto.response;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "티켓 응답")
public record TicketResponse(
        @Schema(description = "티켓 ID", example = "1") Long ticketId,
        @Schema(description = "스케줄 ID", example = "1") Long scheduleId,
        @Schema(description = "예약자 ID") UUID userId,
        @Schema(description = "영화 ID", example = "1") Long movieId,
        @Schema(description = "티켓 상태") TicketStatus status,
        @Schema(description = "상영 시작 시간") LocalDateTime startTime,
        @Schema(description = "상영 종료 시간") LocalDateTime endTime
) {
    public static TicketResponse from(Ticket ticket) {
        Schedule schedule = ticket.getSchedule();
        return new TicketResponse(
                ticket.getId(),
                schedule.getId(),
                ticket.getUserId(),
                schedule.getMovieId(),
                ticket.getStatus(),
                schedule.getStartTime(),
                schedule.getEndTime()
        );
    }
}