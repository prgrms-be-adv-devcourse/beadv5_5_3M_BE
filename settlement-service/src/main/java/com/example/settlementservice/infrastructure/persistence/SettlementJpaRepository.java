package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.application.port.out.SettlementRepository;
import com.example.settlementservice.domain.settlement.Settlement;
import com.example.settlementservice.domain.settlement.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SettlementJpaRepository implements SettlementRepository {

    private final SettlementJpaRepositoryDelegate delegate;

    @Override
    public Optional<Settlement> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Settlement> findByCreatorId(UUID creatorId) {
        return delegate.findByCreatorId(creatorId);
    }

    @Override
    public List<Settlement> findByStatus(SettlementStatus status) {
        return delegate.findByStatus(status);
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return delegate.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Settlement save(Settlement settlement) {
        return delegate.save(settlement);
    }
}