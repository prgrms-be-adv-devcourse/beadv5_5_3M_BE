package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.exception.BusinessException;
import com.example.paymentservice.common.exception.ErrorCode;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEnqueuer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 서비스 트랜잭션 안에서 호출. 같은 TX로 커밋된다.
     * payload는 JSON으로 직렬화되어 outbox_messages.payload에 저장된다.
     */
    public void enqueue(String aggregateType, String aggregateId, String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] payload 직렬화 실패 - topic={}, aggregateId={}", topic, aggregateId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        outboxRepository.save(OutboxMessage.pending(aggregateType, aggregateId, topic, json));
    }
}
