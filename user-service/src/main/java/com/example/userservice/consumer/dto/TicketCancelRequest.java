package com.example.userservice.consumer.dto;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

public record TicketCancelRequest(
        Long ticketId,
        Long scheduleId,
        UUID userId,
        Integer cookieAmount
) {

    public static TicketCancelRequest fromJson(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(message, TicketCancelRequest.class);
    }
}
