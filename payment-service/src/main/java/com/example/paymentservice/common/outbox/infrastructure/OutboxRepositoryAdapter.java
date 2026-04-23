package com.example.paymentservice.common.outbox.infrastructure;

import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxJpaRepository jpaRepository;

    @Override
    public OutboxMessage save(OutboxMessage message) {
        return jpaRepository.save(message);
    }

    @Override
    public List<OutboxMessage> findPendingForRelay(LocalDateTime now, int batchSize) {
        return jpaRepository.findPendingForRelay(now, batchSize);
    }
}
