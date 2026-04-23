
package com.example.paymentservice.common.outbox.infrastructure;

import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Postgres: status=PENDING, next_retry_at <= now 인 row를 id 오름차순으로
     * 배치 크기만큼 잠금 획득 (다른 replica가 이미 잡은 row는 skip).
     *
     * H2 테스트 환경에서는 FOR UPDATE SKIP LOCKED가 무시되거나 미지원일 수 있으므로
     * OutboxRelayTest는 Mockito로 Repository를 모킹한다.
     */
    @Query(
            value = """
                    SELECT * FROM outbox_messages
                    WHERE status = 'PENDING' AND next_retry_at <= :now
                    ORDER BY id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxMessage> findPendingForRelay(@Param("now") LocalDateTime now,
                                            @Param("batchSize") int batchSize);
}
