package com.example.userservice.infrastructure.kafka.consumer.dto;

import java.util.UUID;

public record TicketCancelRequest(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {
}
