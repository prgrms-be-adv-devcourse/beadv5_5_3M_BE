package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementJpaRepositoryDelegate extends JpaRepository<Settlement, Long> {
    List<Settlement> findByCreatorId(UUID creatorId);
    List<Settlement> findByStatus(SettlementStatus status);
    boolean existsByIdempotencyKey(String idempotencyKey);
}