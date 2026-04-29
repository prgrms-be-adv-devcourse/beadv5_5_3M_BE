package com.example.movieservice.presentation.dto.response.schedule;

import com.example.movieservice.domain.model.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "날짜별 스케줄 조회 응답")
public record ScheduleByDateResponse(
        @Schema(description = "스케줄 ID", example = "1")
        Long scheduleId,

        @Schema(description = "영화 ID", example = "10")
        Long movieId,

        @Schema(description = "영화 제목", example = "범죄도시 3")
        String movieTitle,

        @Schema(description = "포스터 이미지 URL")
        String movieImageUrl,

        @Schema(description = "티켓팅 시작 시간")
        LocalDateTime ticketingTime,

        @Schema(description = "상영 시작 시간")
        LocalDateTime startTime,

        @Schema(description = "상영 종료 시간")
        LocalDateTime endTime,

        @Schema(description = "남은 좌석 수", example = "85")
        Integer remainingSeats,

        @Schema(description = "스케줄 상태", example = "SCHEDULED")
        String status
) {
    public static ScheduleByDateResponse from(Schedule schedule) {
        return new ScheduleByDateResponse(
                schedule.getScheduleId(),
                schedule.getMovie().getMovieId(),
                schedule.getMovie().getTitle(),
                schedule.getMovie().getImageUrl(),
                schedule.getTicketingTime(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getRemainingSeats(),
                schedule.getStatus().name()
        );
    }
}