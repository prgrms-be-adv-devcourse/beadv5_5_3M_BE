package com.example.paymentservice.common.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

//topic: payment.refunded
//receiver: user-service
public record PaymentRefundedMessage(
        Long refundId,
        Long paymentId,
        UUID userId,
        Integer amount,
        Integer cookieAmount,
        LocalDateTime createdAt
) {
    public static PaymentRefundedMessage of(Long refundId, Long paymentId, UUID userId,
                                             int amount, int cookieAmount) {
        return new PaymentRefundedMessage(refundId, paymentId, userId, amount, cookieAmount, LocalDateTime.now());
    }
}
