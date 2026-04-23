package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.messaging.PaymentTopics;
import com.example.paymentservice.common.messaging.dto.PaymentConfirmedMessage;
import com.example.paymentservice.common.outbox.OutboxStatus;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEnqueuerTest {

    private OutboxRepository repository;
    private OutboxEnqueuer enqueuer;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        when(repository.save(any(OutboxMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        enqueuer = new OutboxEnqueuer(repository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void enqueue_는_PENDING_상태_OutboxMessage_를_저장한다() {
        UUID userId = UUID.randomUUID();
        PaymentConfirmedMessage payload = PaymentConfirmedMessage.of(42L, userId, 10000, 100);

        enqueuer.enqueue("PAYMENT", "42", PaymentTopics.PAYMENT_CONFIRMED, payload);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        OutboxMessage saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getTopic()).isEqualTo(PaymentTopics.PAYMENT_CONFIRMED);
        assertThat(saved.getPayload()).contains("\"paymentId\":42");
    }
}
