package com.example.userservice.infrastructure.kafka.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentConfirmRequest(
        Long paymentId,
        UUID userId,
        Integer amount,
        Integer cookieAmount,
        LocalDateTime createdAt
) {
}
