package com.example.paymentservice.refund.client;

/**
 * 외부 PG 환불 연동을 위한 인터페이스 포트 (헥사고날 아키텍처)
 * Core 도메인에서는 이 인터페이스만 존재하고,
 * PG 연동 어댑터에서 TossRefundClient가 구현체로 등록됩니다.
 */
public interface RefundGateway {

    RefundGatewayResponse cancelPayment(String paymentKey, int cancelAmount, String cancelReason);

    record RefundGatewayResponse(
            String paymentKey,
            String status,
            int cancelAmount
    ) {}
}
