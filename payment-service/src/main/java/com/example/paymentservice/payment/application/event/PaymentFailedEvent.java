package com.example.paymentservice.payment.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        Long paymentId,
        UUID userId,
        LocalDateTime occurredAt
) {
    public static PaymentFailedEvent of(Long paymentId, UUID userId) {
        return new PaymentFailedEvent(paymentId, userId, LocalDateTime.now());
    }
}
