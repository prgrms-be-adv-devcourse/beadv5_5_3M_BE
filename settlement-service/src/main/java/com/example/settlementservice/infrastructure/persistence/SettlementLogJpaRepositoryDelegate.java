package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.domain.settlement.SettlementLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface SettlementLogJpaRepositoryDelegate extends JpaRepository<SettlementLog, Long> {
    boolean existsBySourceEventId(String sourceEventId);

    @Query("SELECT s.sourceEventId FROM SettlementLog s WHERE s.sourceEventId IN :ids")
    Set<String> findExistingSourceEventIds(@Param("ids") List<String> ids);
}