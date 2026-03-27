package com.example.settlementservice.application.port.out;

import com.example.settlementservice.domain.settlement.SettlementLog;

import java.util.List;
import java.util.Set;

public interface SettlementLogRepository {
    boolean existsBySourceEventId(String sourceEventId);
    Set<String> findExistingSourceEventIds(List<String> sourceEventIds);
    SettlementLog save(SettlementLog log);
    List<SettlementLog> saveAll(List<SettlementLog> logs);
}