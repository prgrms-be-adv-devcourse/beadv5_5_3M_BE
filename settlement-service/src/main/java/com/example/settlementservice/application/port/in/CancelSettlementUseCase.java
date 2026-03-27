package com.example.settlementservice.application.port.in;

import com.example.settlementservice.presentation.web.dto.SettlementResponse;

import java.util.UUID;

public interface CancelSettlementUseCase {
    SettlementResponse cancelSettlement(Long settlementId, UUID creatorId);
}