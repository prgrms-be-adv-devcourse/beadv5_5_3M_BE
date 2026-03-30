package com.example.userservice.consumer.dto;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentRefundRequest(
        Long refundId,
        Long paymentId,
        UUID userId,
        Integer amount,
        Integer cookieAmount,
        LocalDateTime createdAt
) {

    public static PaymentRefundRequest fromJson(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(message, PaymentRefundRequest.class);
    }
}
