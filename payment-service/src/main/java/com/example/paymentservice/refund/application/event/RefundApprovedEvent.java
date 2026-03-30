package com.example.paymentservice.refund.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundApprovedEvent(
        Long refundId,
        Long paymentId,
        UUID userId,
        int amount,
        int cookieAmount,
        LocalDateTime createdAt
) {
    public static RefundApprovedEvent of(Long refundId, Long paymentId, UUID userId,
                                          int amount, int cookieAmount) {
        return new RefundApprovedEvent(refundId, paymentId, userId, amount,
                cookieAmount, LocalDateTime.now());
    }
}
