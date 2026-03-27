package com.example.ticketservice.application.dto.response;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketResponse(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Long movieId,
        TicketStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime
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
