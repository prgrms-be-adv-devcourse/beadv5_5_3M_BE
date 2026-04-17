package com.example.paymentservice.refund.domain;

public enum RefundStatus {
    PENDING,        // 환불 요청됨, 관리자 검토 대기
    PROCESSING,     // PG 취소 요청 중 (이중 승인 방지)
    SUCCESS,        // PG 취소 완료 + DB 반영 완료
    FAILED          // 관리자 거절 or PG 취소 실패
}
