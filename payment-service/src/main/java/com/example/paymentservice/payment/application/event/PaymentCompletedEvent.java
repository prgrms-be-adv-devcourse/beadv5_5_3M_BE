package com.example.paymentservice.payment.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
        Long paymentId,
        UUID userId,
        int amount,
        int cookieAmount,
        LocalDateTime occurredAt
) {
    public static PaymentCompletedEvent of(Long paymentId, UUID userId, int amount, int cookieAmount) {
        return new PaymentCompletedEvent(paymentId, userId, amount, cookieAmount, LocalDateTime.now());
    }
}
