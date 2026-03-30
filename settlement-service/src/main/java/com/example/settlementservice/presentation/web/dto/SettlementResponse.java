package com.example.settlementservice.presentation.web.dto;

import com.example.settlementservice.domain.settlement.Settlement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "정산 응답")
public record SettlementResponse(
        @Schema(description = "정산 ID") Long id,
        @Schema(description = "크리에이터 ID") UUID creatorId,
        @Schema(description = "정산 상태 (REQUESTED / CONFIRMED / COMPLETED / FAILED / CANCELLED)") String status,
        @Schema(description = "정산 신청 금액 (원 단위)") Long requestAmount,
        @Schema(description = "정산 신청 일시") OffsetDateTime requestedAt,
        @Schema(description = "정산 처리 마감일") OffsetDateTime settlementDeadline,
        @Schema(description = "수령 은행명") String payoutBankName,
        @Schema(description = "수령 계좌번호") String payoutAccountNumber,
        @Schema(description = "수령 계좌 예금주") String payoutAccountHolder
) {
    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(
                s.getId(),
                s.getCreatorId(),
                s.getStatus().name(),
                s.getRequestAmount(),
                s.getRequestedAt(),
                s.getSettlementDeadline(),
                s.getPayoutBankName(),
                s.getPayoutAccountNumber(),
                s.getPayoutAccountHolder()
        );
    }
}