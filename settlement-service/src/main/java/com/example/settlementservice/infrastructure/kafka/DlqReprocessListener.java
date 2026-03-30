package com.example.settlementservice.infrastructure.kafka;

import com.example.settlementservice.application.port.in.IngestRevenueUseCase;
import com.example.settlementservice.infrastructure.persistence.PermanentFailureEvent;
import com.example.settlementservice.infrastructure.persistence.PermanentFailureJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


@Slf4j
@Component
@RequiredArgsConstructor
public class DlqReprocessListener {

    private final IngestRevenueUseCase ingestRevenueUseCase;
    private final ObjectMapper objectMapper;
    private final DlqProducer dlqProducer;
    private final PermanentFailureJpaRepository permanentFailureRepository;

    @Value("${settlement.dlq-max-retry:3}")
    private int maxRetry;

    @KafkaListener(
            topics = DlqProducer.DLQ_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}-dlq-reprocess",
            containerFactory = "singleKafkaListenerContainerFactory"
    )
    public void reprocess(ConsumerRecord<String, String> record, Acknowledgment ack) {
        DlqMessage dlqMessage = parseDlqMessage(record);

        if (dlqMessage == null) {
            // DLQ 메시지 자체를 파싱할 수 없음 → 즉시 영구 저장
            savePermanent(PermanentFailureEvent.ofRaw(record.value(),
                    new IllegalArgumentException("Cannot parse DLQ message")));
            ack.acknowledge();
            return;
        }

        try {
            RevenueIngestEventPayload payload = objectMapper.readValue(
                    dlqMessage.originalPayload(), RevenueIngestEventPayload.class);

            ingestRevenueUseCase.ingestRevenue(
                    payload.creatorId(),
                    payload.ticketId(),
                    payload.scheduleId(),
                    payload.cookieAmount()
            );

            log.info("DLQ reprocess success (retryCount={}): originalTopic={}, originalOffset={}",
                    dlqMessage.retryCount(), dlqMessage.topic(), dlqMessage.offset());

        } catch (DataIntegrityViolationException e) {
            // source_event_id UNIQUE 제약 위반 → 이미 처리된 중복 이벤트, 정상 완료로 간주
            log.info("DLQ duplicate detected via UNIQUE constraint (retryCount={}): originalOffset={} — skipping",
                    dlqMessage.retryCount(), dlqMessage.offset());
        } catch (Exception e) {
            handleReprocessFailure(dlqMessage, e);
        }

        ack.acknowledge();
    }

    /**
     * 재처리 실패 시 retryCount에 따라 재투입 또는 영구 저장을 결정한다.
     */
    private void handleReprocessFailure(DlqMessage dlqMessage, Exception cause) {
        int nextRetryCount = dlqMessage.retryCount() + 1;

        if (nextRetryCount < maxRetry) {
            dlqProducer.resend(dlqMessage, cause);
            log.warn("DLQ reprocess failed, re-queuing ({}/{}): originalOffset={}, error={}",
                    nextRetryCount, maxRetry, dlqMessage.offset(), cause.getMessage());
        } else {
            savePermanent(PermanentFailureEvent.of(dlqMessage, cause));
            log.error("CRITICAL: Max retries ({}) exceeded. Saved to permanent failure store. " +
                    "originalTopic={}, originalOffset={}, error={}",
                    maxRetry, dlqMessage.topic(), dlqMessage.offset(), cause.getMessage());
        }
    }

    private DlqMessage parseDlqMessage(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), DlqMessage.class);
        } catch (Exception e) {
            log.error("Cannot parse DLQ message envelope: offset={}", record.offset(), e);
            return null;
        }
    }

    private void savePermanent(PermanentFailureEvent event) {
        try {
            permanentFailureRepository.save(event);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to save permanent failure to DB. MANUAL RECOVERY REQUIRED. " +
                    "payload={}", event.getOriginalPayload(), e);
        }
    }
}