package com.example.paymentservice.refund.client.toss.dto;

public record TossRefundRequest(
        String cancelReason,
        int cancelAmount
) {
}
