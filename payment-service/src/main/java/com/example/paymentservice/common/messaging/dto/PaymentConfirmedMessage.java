package com.example.paymentservice.common.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

//topic: payment.confirmed
//receiver: user-service
public record PaymentConfirmedMessage(
        Long paymentId,
        UUID userId,
        Integer amount,
        Integer cookieAmount,
        LocalDateTime createdAt
) {
    public static PaymentConfirmedMessage of(Long paymentId, UUID userId, int amount, int cookieAmount) {
        return new PaymentConfirmedMessage(paymentId, userId, amount, cookieAmount, LocalDateTime.now());
    }
}
