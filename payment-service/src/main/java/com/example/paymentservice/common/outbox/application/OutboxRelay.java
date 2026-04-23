package com.example.paymentservice.common.outbox.application;

import com.example.paymentservice.common.outbox.OutboxProperties;
import com.example.paymentservice.common.outbox.domain.model.OutboxMessage;
import com.example.paymentservice.common.outbox.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;

    /**
     * 주기적으로 PENDING 메시지를 가져와 Kafka로 발행한다.
     *
     * @Transactional: findPendingForRelay의 FOR UPDATE SKIP LOCKED 잠금을 유지하기 위해
     *                 트랜잭션이 필요하다. 배치 처리 후 커밋되면 잠금이 해제된다.
     */
    @Scheduled(fixedDelayString = "${payment.outbox.poll-delay-ms:500}")
    @Transactional
    public void relay() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxMessage> batch = outboxRepository.findPendingForRelay(now, properties.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxMessage msg : batch) {
            publishOne(msg);
        }
    }

    private void publishOne(OutboxMessage msg) {
        try {
            // Producer의 value-serializer가 JsonSerializer라, 저장된 JSON 문자열을
            // 그대로 넘기면 이중 직렬화되어 이스케이프된 JSON이 브로커에 저장된다.
            // JsonNode로 파싱해서 넘기면 JsonSerializer가 정상 직렬화하며,
            // Consumer는 StringDeserializer로 JSON 문자열을 받아 파싱하는 기존 계약 유지.
            JsonNode value = objectMapper.readTree(msg.getPayload());

            kafkaTemplate
                    .send(msg.getTopic(), msg.getAggregateId(), value)
                    .get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            msg.markPublished();
            outboxRepository.save(msg);
        } catch (Exception e) {
            handleFailure(msg, e);
        }
    }

    private void handleFailure(OutboxMessage msg, Exception e) {
        int nextRetry = msg.getRetryCount() + 1;
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (nextRetry >= properties.maxRetries()) {
            log.error("[Outbox] MAX 재시도 초과 - FAILED 전이. messageId={}, topic={}, error={}",
                    msg.getMessageId(), msg.getTopic(), error, e);
            msg.markFailed(error);
        } else {
            long backoffSeconds = (long) (properties.backoffBaseSeconds() * Math.pow(2, msg.getRetryCount()));
            LocalDateTime next = LocalDateTime.now().plusSeconds(backoffSeconds);
            log.warn("[Outbox] 발행 실패 - 재시도 예약. messageId={}, retry={}/{}, nextRetryAt={}, error={}",
                    msg.getMessageId(), nextRetry, properties.maxRetries(), next, error);
            msg.markRetry(error, next);
        }
        outboxRepository.save(msg);
    }
}
