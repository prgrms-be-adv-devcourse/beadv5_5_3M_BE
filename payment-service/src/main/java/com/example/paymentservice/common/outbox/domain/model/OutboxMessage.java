package com.example.paymentservice.common.outbox.domain.model;

import com.example.paymentservice.common.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_next_retry",
                        columnList = "status, next_retry_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static OutboxMessage pending(String aggregateType,
                                        String aggregateId,
                                        String topic,
                                        String payload) {
        OutboxMessage m = new OutboxMessage();
        m.messageId = UUID.randomUUID();
        m.aggregateType = aggregateType;
        m.aggregateId = aggregateId;
        m.topic = topic;
        m.payload = payload;
        m.status = OutboxStatus.PENDING;
        m.retryCount = 0;
        m.createdAt = LocalDateTime.now();
        m.nextRetryAt = m.createdAt;
        return m;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markRetry(String error, LocalDateTime nextRetryAt) {
        this.retryCount++;
        this.lastError = truncate(error);
        this.nextRetryAt = nextRetryAt;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
