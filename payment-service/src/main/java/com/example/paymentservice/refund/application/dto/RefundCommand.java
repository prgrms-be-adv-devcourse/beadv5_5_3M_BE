package com.example.paymentservice.refund.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "환불 요청 명령")
public record RefundCommand(
        @Schema(description = "원래 결제 ID") Long paymentId,
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "환불 금액 (원)") int amount,
        @Schema(description = "환불 쿠키 수량") int cookieAmount
) {
}
