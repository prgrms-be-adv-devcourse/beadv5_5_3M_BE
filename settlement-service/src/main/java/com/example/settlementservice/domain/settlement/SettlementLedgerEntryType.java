package com.example.settlementservice.domain.settlement;

public enum SettlementLedgerEntryType {
    REVENUE_INGEST,              // 쿠키 수익 적립
    SETTLEMENT_REQUEST_DEBIT,    // 정산 신청 시 지갑 차감
    SETTLEMENT_PAYOUT,           // 목요일 지급 완료 로그 (감사용)
    SETTLEMENT_CANCEL_REFUND     // 정산 취소 환급 / 지급 실패 환급
}