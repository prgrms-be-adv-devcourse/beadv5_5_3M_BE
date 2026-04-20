package com.example.paymentservice.payment.application.dto;

import java.util.UUID;

public record PaymentConfirmCommand(UUID userId, String paymentKey, String orderId, int amount, int cookieAmount) {
}
