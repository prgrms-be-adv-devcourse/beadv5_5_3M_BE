package com.example.paymentservice.refund.presentation.dto;

import com.example.paymentservice.refund.application.dto.RefundCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Schema(description = "환불 요청")
public record RefundRequest(
        @Schema(description = "원래 결제 ID") @NotNull Long paymentId,
        @Schema(description = "사용자 ID") @NotNull UUID userId,
        @Schema(description = "환불 금액 (원)") @Positive int amount,
        @Schema(description = "환불 쿠키 수량") @Positive int cookieAmount
) {
    public RefundCommand toCommand() {
        return new RefundCommand(paymentId, userId, amount, cookieAmount);
    }
}
