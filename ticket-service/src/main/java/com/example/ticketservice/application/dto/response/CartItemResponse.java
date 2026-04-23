package com.example.ticketservice.application.dto.response;

import com.example.ticketservice.domain.model.Cart;
import com.example.ticketservice.domain.model.Schedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "장바구니 항목 응답")
public record CartItemResponse(
        @Schema(description = "스케줄 ID") Long scheduleId,
        @Schema(description = "영화 ID") Long movieId,
        @Schema(description = "공연 제목") String title,
        @Schema(description = "공연 시작 시간") LocalDateTime startTime,
        @Schema(description = "공연 종료 시간") LocalDateTime endTime,
        @Schema(description = "티켓팅 시작 시간") LocalDateTime ticketingTime,
        @Schema(description = "쿠키 가격") Integer cookie
) {
    public static CartItemResponse from(Cart cart, Schedule schedule) {
        return new CartItemResponse(
                schedule.getId(),
                schedule.getMovieId(),
                schedule.getTitle(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getTicketingTime(),
                schedule.getCookie()
        );
    }
}