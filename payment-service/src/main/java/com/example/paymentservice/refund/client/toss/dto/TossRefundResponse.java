package com.example.paymentservice.refund.client.toss.dto;

import com.example.paymentservice.refund.client.RefundGateway;

public record TossRefundResponse(
        String paymentKey,
        String status,
        int totalAmount
) {
    public RefundGateway.RefundGatewayResponse toGatewayResponse(int cancelAmount) {
        return new RefundGateway.RefundGatewayResponse(paymentKey, status, cancelAmount);
    }
}
