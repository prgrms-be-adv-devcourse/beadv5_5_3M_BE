package com.example.paymentservice.payment.application.dto;

import java.util.UUID;

public record PaymentConfirmCommand(Long paymentId, String paymentKey, String orderId) {
}
