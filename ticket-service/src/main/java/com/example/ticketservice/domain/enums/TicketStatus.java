package com.example.ticketservice.domain.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "티켓 상태")
public enum TicketStatus {
    AVAILABLE, //예매 가능 상태
    RESERVED, //예약 상태
    CONFIRMED, //확정 상태
    HOLD // 판매 중지
}