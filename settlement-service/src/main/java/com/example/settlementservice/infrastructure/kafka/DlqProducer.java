package com.example.settlementservice.infrastructure.kafka;

import com.example.settlementservice.infrastructure.persistence.PermanentFailureEvent;
import com.example.settlementservice.infrastructure.persistence.PermanentFailureJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqProducer {

    static final String DLQ_TOPIC = "settlement.revenue.dlq";
    private static final int MAX_SEND_RETRY = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PermanentFailureJpaRepository permanentFailureJpaRepository;

    /**
     * 최초 실패 레코드를 DLQ로 전송한다 (retryCount = 0).
     */
    public void send(ConsumerRecord<String, String> record, Exception cause) {
        DlqMessage dlqMessage = new DlqMessage(
                record.value(),
                cause.getMessage(),
                record.topic(),
                record.partition(),
                record.offset(),
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                0
        );
        sendToKafka(dlqMessage, String.valueOf(record.partition()));
    }

    /**
     * DLQ 재처리 실패 시 retryCount를 1 증가시켜 DLQ에 재투입한다.
     * occurredAt은 최초 실패 시각을 그대로 유지한다.
     */
    public void resend(DlqMessage original, Exception cause) {
        DlqMessage retried = new DlqMessage(
                original.originalPayload(),
                cause.getMessage(),
                original.topic(),
                original.partition(),
                original.offset(),
                original.occurredAt(),   // 최초 실패 시각 보존
                original.retryCount() + 1
        );
        sendToKafka(retried, String.valueOf(original.partition()));
    }

    private void sendToKafka(DlqMessage dlqMessage, String key) {
        String payload = serialize(dlqMessage);
        if (payload == null) return;

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_SEND_RETRY; attempt++) {
            try {
                kafkaTemplate.send(DLQ_TOPIC, key, payload).get();
                log.warn("Sent to DLQ (attempt={}/{}, retryCount={}): topic={}, partition={}, offset={}",
                        attempt, MAX_SEND_RETRY, dlqMessage.retryCount(),
                        dlqMessage.topic(), dlqMessage.partition(), dlqMessage.offset());
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("DLQ send attempt {}/{} failed: {}", attempt, MAX_SEND_RETRY, e.getMessage());
            }
        }

        persistToFallbackFile(payload, dlqMessage, lastException);
    }

    private String serialize(DlqMessage dlqMessage) {
        try {
            return objectMapper.writeValueAsString(dlqMessage);
        } catch (Exception e) {
            log.error("CRITICAL: Cannot serialize DLQ message. MANUAL RECOVERY REQUIRED. " +
                    "topic={}, partition={}, offset={}, originalPayload={}",
                    dlqMessage.topic(), dlqMessage.partition(), dlqMessage.offset(),
                    dlqMessage.originalPayload(), e);
            return null;
        }
    }

    private void persistToFallbackFile(String payload, DlqMessage dlqMessage, Exception cause) {
        try {
            permanentFailureJpaRepository.save(PermanentFailureEvent.of(dlqMessage, cause));
            log.error("CRITICAL: DLQ unavailable after {} retries. Persisted to DB permanent_failures. " +
                            "topic={}, partition={}, offset={}",
                    MAX_SEND_RETRY, dlqMessage.topic(), dlqMessage.partition(), dlqMessage.offset(), cause);
        } catch (Exception e) {
            log.error("CRITICAL: Both DLQ and DB fallback failed. MANUAL RECOVERY REQUIRED. " +
                            "topic={}, partition={}, offset={}, payload={}",
                    dlqMessage.topic(), dlqMessage.partition(), dlqMessage.offset(), payload, e);
        }
    }
}