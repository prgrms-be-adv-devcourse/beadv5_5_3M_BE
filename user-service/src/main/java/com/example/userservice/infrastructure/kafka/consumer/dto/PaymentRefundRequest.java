package com.example.userservice.infrastructure.kafka.consumer.dto;

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
}
