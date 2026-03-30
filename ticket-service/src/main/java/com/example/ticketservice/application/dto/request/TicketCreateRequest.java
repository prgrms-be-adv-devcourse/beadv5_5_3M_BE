package com.example.ticketservice.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "티켓 예약 요청")
public record TicketCreateRequest(
        @Schema(description = "예약할 스케줄 ID", example = "1") Long scheduleId
) {
}