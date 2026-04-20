package com.example.creatorservice.infrastructure.kafka;

import com.example.creatorservice.event.CreatorCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreatorCreated(CreatorCreatedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("creator.created", event.creatorId().toString(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Kafka] creator.created 발행 실패 - creatorId: {}", event.creatorId(), ex);
                        } else {
                            log.info("[Kafka] creator.created 발행 성공 - creatorId: {}", event.creatorId());
                        }
                    });
        } catch (Exception e) {
            log.error("[Kafka] creator.created 직렬화 실패 - creatorId: {}", event.creatorId(), e);
        }
    }
}