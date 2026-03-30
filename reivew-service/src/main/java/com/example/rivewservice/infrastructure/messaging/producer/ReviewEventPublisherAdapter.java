package com.example.rivewservice.infrastructure.messaging.producer;

import com.example.rivewservice.application.port.out.ReviewEventPublisherPort;
import com.example.rivewservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventPublisherAdapter implements ReviewEventPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMessageUtil kafkaMessageUtil;

    @Override
    public void publish(String topic, String key, Object payload) {
        String message = kafkaMessageUtil.serialize(payload);
        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 발행 실패 - topic: {}, key: {}", topic, key, ex);
                    } else {
                        log.debug("Kafka 발행 성공 - topic: {}, key: {}", topic, key);
                    }
                });
    }
}