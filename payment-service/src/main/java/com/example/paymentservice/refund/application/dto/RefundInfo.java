package com.example.paymentservice.refund.application.dto;

import com.example.paymentservice.refund.domain.RefundStatus;
import com.example.paymentservice.refund.domain.model.Refund;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "환불 응답")
public record RefundInfo(
        @Schema(description = "환불 ID") Long id,
        @Schema(description = "원래 결제 ID") Long paymentId,
        @Schema(description = "환불 금액 (원)") int amount,
        @Schema(description = "환불 쿠키 수량") int cookieAmount,
        @Schema(description = "환불 상태") RefundStatus status,
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "환불 생성 시각") LocalDateTime createdAt
) {

    public static RefundInfo from(Refund refund) {
        return new RefundInfo(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getCookieAmount(),
                refund.getStatus(),
                refund.getUserId(),
                refund.getCreatedAt()
        );
    }
}
