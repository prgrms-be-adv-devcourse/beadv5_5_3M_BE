package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.outbox.OutboxProperties;
import com.example.paymentservice.common.outbox.OutboxStatus;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private OutboxRepository repository;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxRepository.class);
        kafkaTemplate = (KafkaTemplate<String, Object>) mock(KafkaTemplate.class);
        OutboxProperties props = new OutboxProperties();
        props.setBatchSize(100);
        props.setPollDelayMs(500L);
        props.setSendTimeoutMs(3_000L);
        props.setMaxRetries(5);
        props.setBackoffBaseSeconds(10);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        relay = new OutboxRelay(repository, kafkaTemplate, objectMapper, props);
    }

    private OutboxMessage pending(long id) {
        return OutboxMessage.pending("PAYMENT", String.valueOf(id),
                "payment.confirmed", "{\"paymentId\":" + id + "}");
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> successFuture() {
        SendResult<String, Object> result = (SendResult<String, Object>) mock(SendResult.class);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<SendResult<String, Object>> failedFuture(String reason) {
        CompletableFuture<SendResult<String, Object>> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException(reason));
        return f;
    }

    @Test
    void 성공_발행_시_PUBLISHED_로_마킹한다() {
        OutboxMessage m = pending(1L);
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of(m));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        relay.relay();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(m.getPublishedAt()).isNotNull();
        verify(repository).save(m);
    }

    @Test
    void 실패시_retryCount_증가하고_PENDING_유지한다() {
        OutboxMessage m = pending(1L);
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of(m));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture("boom"));

        relay.relay();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(m.getRetryCount()).isEqualTo(1);
        assertThat(m.getLastError()).contains("boom");
        verify(repository).save(m);
    }

    @Test
    void maxRetries_도달_직전_실패하면_FAILED_로_전이한다() {
        OutboxMessage m = pending(1L);
        for (int i = 0; i < 4; i++) {
            m.markRetry("prev", m.getNextRetryAt());
        }
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of(m));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture("final"));

        relay.relay();

        assertThat(m.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(m.getRetryCount()).isEqualTo(5);
        verify(repository).save(m);
    }

    @Test
    void findPending_결과가_비면_Kafka_호출_안_한다() {
        when(repository.findPendingForRelay(any(), anyInt())).thenReturn(List.of());

        relay.relay();

        verifyNoInteractions(kafkaTemplate);
    }
}
