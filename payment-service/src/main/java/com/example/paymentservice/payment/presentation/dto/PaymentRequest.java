package com.example.paymentservice.payment.presentation.dto;

import com.example.paymentservice.payment.application.dto.PaymentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(description = "결제 생성 요청")
public record PaymentRequest(
        @Schema(description = "결제 금액 (원)") @Positive int amount,
        @Schema(description = "쿠키 수량") @Positive int cookieAmount
) {
    public PaymentCommand toCommand(UUID userId) {
        return new PaymentCommand(userId, amount, cookieAmount);
    }
}
