package com.example.paymentservice.payment.client.toss.dto;

public record TossPaymentConfirmRequest(
        String paymentKey,
        String orderId,
        int amount
) {
}
