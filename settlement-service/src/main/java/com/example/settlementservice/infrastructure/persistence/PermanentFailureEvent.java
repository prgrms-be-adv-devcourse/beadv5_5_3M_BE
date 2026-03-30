package com.example.settlementservice.infrastructure.persistence;

import com.example.settlementservice.infrastructure.kafka.DlqMessage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "dlq_permanent_failures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermanentFailureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 Kafka 메시지 페이로드 */
    @Column(name = "original_payload", columnDefinition = "text", nullable = false)
    private String originalPayload;

    /** 마지막 재처리 실패 원인 */
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    /** 원본 Kafka 토픽 */
    @Column(name = "source_topic")
    private String sourceTopic;

    /** 원본 Kafka 파티션 */
    @Column(name = "source_partition")
    private Integer sourcePartition;

    /** 원본 Kafka 오프셋 */
    @Column(name = "source_offset")
    private Long sourceOffset;

    /** 시도한 재처리 횟수 (max retry 도달 시점의 retryCount) */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "failed_at", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime failedAt;

    @PrePersist
    void onPersist() {
        this.failedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * DLQ 메시지 기반 생성 (정상 파싱된 경우).
     */
    public static PermanentFailureEvent of(DlqMessage dlq, Exception cause) {
        PermanentFailureEvent e = new PermanentFailureEvent();
        e.originalPayload = dlq.originalPayload();
        e.lastError = cause.getMessage();
        e.sourceTopic = dlq.topic();
        e.sourcePartition = dlq.partition();
        e.sourceOffset = dlq.offset();
        e.retryCount = dlq.retryCount();
        return e;
    }

    /**
     * DLQ 메시지 자체를 파싱할 수 없는 경우 (raw payload 그대로 저장).
     */
    public static PermanentFailureEvent ofRaw(String rawPayload, Exception cause) {
        PermanentFailureEvent e = new PermanentFailureEvent();
        e.originalPayload = rawPayload;
        e.lastError = cause != null ? cause.getMessage() : "Unknown — DLQ message parse failure";
        e.retryCount = 0;
        return e;
    }
}