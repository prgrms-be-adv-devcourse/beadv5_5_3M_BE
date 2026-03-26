package com.example.paymentservice.payment.application.dto;

import com.example.paymentservice.payment.domain.PaymentStatus;
import com.example.paymentservice.payment.domain.model.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "결제 응답")
public record PaymentInfo(
        @Schema(description = "결제 ID") Long id,
        @Schema(description = "결제 금액 (원)") int amount,
        @Schema(description = "쿠키 수량") int cookieAmount,
        @Schema(description = "결제 상태") PaymentStatus status,
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "토스 결제 키") String paymentKey,
        @Schema(description = "결제 생성 시각") LocalDateTime createdAt
) {

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getAmount(),
                payment.getCookieAmount(),
                payment.getStatus(),
                payment.getUserId(),
                payment.getPaymentKey(),
                payment.getCreatedAt()
        );
    }
}
