package com.example.userservice.infrastructure.kafka.consumer.dto;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentConfirmRequest(
        Long paymentId,
        UUID userId,
        Integer amount,
        Integer cookieAmount,
        LocalDateTime createdAt
) {
    public static PaymentConfirmRequest fromJson(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(message, PaymentConfirmRequest.class);
    }
}
