package com.example.settlementservice.infrastructure.kafka;

import com.example.settlementservice.application.service.WalletCreationService;
import com.example.settlementservice.infrastructure.persistence.PermanentFailureEvent;
import com.example.settlementservice.infrastructure.persistence.PermanentFailureJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorCreatedListener {

    private final WalletCreationService walletCreationService;
    private final ObjectMapper objectMapper;
    private final PermanentFailureJpaRepository permanentFailureRepository;

    @RetryableTopic(
            attempts = "4",  // 1회 원본 + 3회 재시도
            backOff = @BackOff(delay = 1000, multiplier = 2),
            dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR,
            listenerContainerFactory = "retryableKafkaListenerContainerFactory"
    )
    @KafkaListener(topics = "creator.created", groupId = "settlement-service", containerFactory = "retryableKafkaListenerContainerFactory")
    public void consume(
            String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        try {
            CreatorCreatedMessage msg = objectMapper.readValue(message, CreatorCreatedMessage.class);
            log.info("[Kafka] creator.created 수신 - creatorId: {}, partition: {}, offset: {}",
                    msg.creatorId(), partition, offset);
            walletCreationService.findOrCreate(msg.creatorId());
            log.info("[Kafka] creator.created 처리 완료 - creatorId: {}", msg.creatorId());
        } catch (Exception e) {
            log.error("[Kafka] creator.created 처리 실패 - partition: {}, offset: {}, message: {}",
                    partition, offset, message, e);
            throw e;  // @RetryableTopic retry 토픽으로 넘기기 위해 rethrow
        }
    }

    @DltHandler
    public void handleDlt(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.error("CRITICAL: creator.created 최종 실패 (DLT) - topic: {}, partition: {}, offset: {}, message: {}",
                topic, partition, offset, message);
        try {
            permanentFailureRepository.save(PermanentFailureEvent.ofRaw(message,
                    new RuntimeException("creator.created: 모든 재시도 소진")));
        } catch (Exception e) {
            log.error("CRITICAL: permanent_failures 저장 실패. MANUAL RECOVERY REQUIRED. message={}", message, e);
        }
    }
}