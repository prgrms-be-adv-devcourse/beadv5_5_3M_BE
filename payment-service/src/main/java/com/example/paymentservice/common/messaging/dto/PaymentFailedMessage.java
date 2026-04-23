package com.example.paymentservice.common.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

//topic: payment.failed
//receiver: user-service (informational)
public record PaymentFailedMessage(
        Long paymentId,
        UUID userId,
        LocalDateTime createdAt
) {
    public static PaymentFailedMessage of(Long paymentId, UUID userId) {
        return new PaymentFailedMessage(paymentId, userId, LocalDateTime.now());
    }
}
