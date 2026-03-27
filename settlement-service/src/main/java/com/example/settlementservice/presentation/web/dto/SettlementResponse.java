package com.example.settlementservice.presentation.web.dto;

import com.example.settlementservice.domain.settlement.Settlement;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SettlementResponse(
        Long id,
        UUID creatorId,
        String status,
        Long requestAmount,
        OffsetDateTime requestedAt,
        OffsetDateTime settlementDeadline,
        String payoutBankName,
        String payoutAccountNumber,
        String payoutAccountHolder
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