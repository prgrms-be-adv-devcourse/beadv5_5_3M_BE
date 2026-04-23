package com.example.paymentservice.common.outbox.domain.model;

import com.example.paymentservice.common.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageTest {

    @Test
    void pending_상태로_생성된다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "42", "payment.confirmed", "{}");

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(m.getRetryCount()).isZero();
        assertThat(m.getMessageId()).isNotNull();
        assertThat(m.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(m.getAggregateId()).isEqualTo("42");
        assertThat(m.getTopic()).isEqualTo("payment.confirmed");
        assertThat(m.getPayload()).isEqualTo("{}");
        assertThat(m.getNextRetryAt()).isNotNull();
        assertThat(m.getCreatedAt()).isNotNull();
    }

    @Test
    void markPublished_상태와_publishedAt_을_세팅한다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");

        m.markPublished();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(m.getPublishedAt()).isNotNull();
    }

    @Test
    void markRetry_retryCount_증가하고_nextRetryAt_을_갱신한다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");
        LocalDateTime next = LocalDateTime.now().plusSeconds(10);

        m.markRetry("boom", next);

        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getLastError()).isEqualTo("boom");
        assertThat(m.getNextRetryAt()).isEqualTo(next);
        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void markFailed_상태와_에러를_저장한다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");

        m.markFailed("max exceeded");

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getLastError()).isEqualTo("max exceeded");
    }

    @Test
    void lastError_2000자_초과시_잘린다() {
        OutboxMessage m = OutboxMessage.pending("PAYMENT", "1", "t", "{}");
        String longError = "x".repeat(2500);

        m.markFailed(longError);

        assertThat(m.getLastError()).hasSize(2000);
    }
}
