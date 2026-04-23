package com.example.paymentservice.payment.presentation.dto;

import com.example.paymentservice.payment.application.dto.PaymentConfirmCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(description = "결제 승인 요청")
public record PaymentConfirmRequest(
        @Schema(description = "토스 결제 키") @NotBlank String paymentKey,
        @Schema(description = "주문 ID") @NotBlank String orderId,
        @Schema(description = "결제 금액 (원)") @Positive int amount,
        @Schema(description = "쿠키 수량") @Positive int cookieAmount
) {
    public PaymentConfirmCommand toCommand(UUID userId) {
        return new PaymentConfirmCommand(userId, paymentKey, orderId, amount, cookieAmount);
    }
}
