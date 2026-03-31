package com.example.settlementservice.application.port.out;

import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository {
    Optional<Settlement> findById(Long id);
    List<Settlement> findByCreatorId(UUID creatorId);
    List<Settlement> findByStatus(SettlementStatus status);
    boolean existsByIdempotencyKey(String idempotencyKey);
    Settlement save(Settlement settlement);
}