package com.example.settlementservice.application.port.in;

import com.example.settlementservice.presentation.web.dto.PostSettlementRequest;
import com.example.settlementservice.presentation.web.dto.SettlementResponse;

import java.util.UUID;

public interface RequestSettlementUseCase {
    SettlementResponse requestSettlement(UUID creatorId, String idempotencyKey, PostSettlementRequest request);
}