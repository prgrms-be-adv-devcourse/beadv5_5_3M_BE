package com.example.ticketservice.domain.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "티켓 상태")
public enum TicketStatus {
    RESERVED,
    CONFIRMED
}