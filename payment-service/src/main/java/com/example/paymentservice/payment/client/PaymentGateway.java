package com.example.paymentservice.payment.client;

public interface PaymentGateway {

    PaymentGatewayResponse confirmPayment(String paymentKey, String orderId, int amount);

    PaymentGatewayResponse cancelPayment(String paymentKey, String cancelReason);

    record PaymentGatewayResponse(
            String paymentKey,
            String orderId,
            String status,
            int totalAmount
    ) {}
}
