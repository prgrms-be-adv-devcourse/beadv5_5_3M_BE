package com.example.creatorservice.presentation.dto.res;

import com.example.creatorservice.domain.model.Schedule;

import java.time.LocalDateTime;

public record ScheduleResponse(
        Long scheduleId,
        Long movieId,
        String movieTitle,
        String movieImageUrl,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime ticketingTime,
        Boolean isConfirmed,
        String status,
        Integer totalSeats,
        Integer remainingSeats
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getScheduleId(),
                schedule.getMovie().getMovieId(),
                schedule.getMovie().getTitle(),
                schedule.getMovie().getImageUrl(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getTicketingTime(),
                schedule.getIsConfirmed(),
                schedule.getStatus().name(),
                schedule.getTotalSeats(),
                schedule.getRemainingSeats()
        );
    }
}