package com.example.userservice.infrastructure.kafka;

import com.example.userservice.application.port.KafkaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAdapter implements KafkaPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaUtil kafkaUtil;

    @Override
    public void publish(String topic, String key, Object payload) {
        String message = kafkaUtil.serialize(payload);
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
