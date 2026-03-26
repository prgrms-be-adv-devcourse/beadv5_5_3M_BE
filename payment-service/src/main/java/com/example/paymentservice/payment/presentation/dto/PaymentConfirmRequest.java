package com.example.paymentservice.payment.presentation.dto;

import com.example.paymentservice.payment.application.dto.PaymentConfirmCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "결제 승인 요청")
public record PaymentConfirmRequest(
        @Schema(description = "결제 ID") @NotNull Long paymentId,
        @Schema(description = "토스 결제 키") @NotBlank String paymentKey,
        @Schema(description = "주문 ID") @NotBlank String orderId
) {
    public PaymentConfirmCommand toCommand() {
        return new PaymentConfirmCommand(paymentId, paymentKey, orderId);
    }
}
