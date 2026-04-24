package com.example.paymentservice.common.outbox.domain.repository;

import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository {

    OutboxMessage save(OutboxMessage message);

    /**
     * 릴레이가 처리할 PENDING row를 잠금과 함께 가져온다.
     * Postgres의 FOR UPDATE SKIP LOCKED를 사용해 여러 replica 간 경합 방지.
     */
    List<OutboxMessage> findPendingForRelay(LocalDateTime now, int batchSize);
}
