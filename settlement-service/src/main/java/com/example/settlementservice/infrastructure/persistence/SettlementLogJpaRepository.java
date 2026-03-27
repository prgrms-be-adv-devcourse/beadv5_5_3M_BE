package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.application.port.out.SettlementLogRepository;
import com.example.settlementservice.domain.settlement.SettlementLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class SettlementLogJpaRepository implements SettlementLogRepository {

    private final SettlementLogJpaRepositoryDelegate delegate;

    @Override
    public boolean existsBySourceEventId(String sourceEventId) {
        return delegate.existsBySourceEventId(sourceEventId);
    }

    @Override
    public Set<String> findExistingSourceEventIds(List<String> sourceEventIds) {
        return delegate.findExistingSourceEventIds(sourceEventIds);
    }

    @Override
    public SettlementLog save(SettlementLog log) {
        return delegate.save(log);
    }

    @Override
    public List<SettlementLog> saveAll(List<SettlementLog> logs) {
        return delegate.saveAll(logs);
    }
}