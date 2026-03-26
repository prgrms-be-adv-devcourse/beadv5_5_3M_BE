package com.example.ticketservice.infrastructure.messaging.producer;

import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaBulkEventPublisher implements EventPublisherPort {

    // 대량 배치 발행 전용 템플릿 (batch.size 64KB, linger.ms 20, snappy 압축)
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMessageUtil kafkaMessageUtil;

    public KafkaBulkEventPublisher(
            @Qualifier("bulkKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            KafkaMessageUtil kafkaMessageUtil) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaMessageUtil = kafkaMessageUtil;
    }

    @Override
    public void publish(String topic, String key, Object payload) {
        String message = kafkaMessageUtil.serialize(payload);
        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 대량 발행 실패 - topic: {}, key: {}", topic, key, ex);
                    } else {
                        log.debug("Kafka 대량 발행 성공 - topic: {}, key: {}", topic, key);
                    }
                });
    }
}