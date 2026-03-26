package com.example.paymentservice.payment.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "결제 생성 명령")
public record PaymentCommand(
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "결제 금액 (원)") int amount,
        @Schema(description = "쿠키 수량") int cookieAmount
) {
}
