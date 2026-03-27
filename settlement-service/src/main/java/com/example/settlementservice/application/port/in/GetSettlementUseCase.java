package com.example.settlementservice.application.port.in;

import com.example.settlementservice.presentation.web.dto.SettlementResponse;

import java.util.List;
import java.util.UUID;

public interface GetSettlementUseCase {
    SettlementResponse getSettlement(Long id, UUID creatorId);
    List<SettlementResponse> getSettlementsByCreatorId(UUID creatorId);
}