package com.example.paymentservice.payment.client.toss.dto;

import com.example.paymentservice.payment.client.PaymentGateway;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        int totalAmount,
        String method,
        String requestedAt,
        String approvedAt
) {
    public PaymentGateway.PaymentGatewayResponse toGatewayResponse() {
        return new PaymentGateway.PaymentGatewayResponse(paymentKey, orderId, status, totalAmount);
    }
}
