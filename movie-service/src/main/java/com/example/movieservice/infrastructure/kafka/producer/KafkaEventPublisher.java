package com.example.movieservice.infrastructure.kafka.producer;

import com.example.movieservice.application.event.EventPublisher;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String topic, String key, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            log.info("[Kafka] 발행 시도 - topic: {}, key: {}, payload: {}", topic, key, message);
            kafkaTemplate.send(topic, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Kafka] 발행 실패 - topic: {}, key: {}, payload: {}", topic, key, message, ex);
                        } else {
                            log.info("[Kafka] 발행 성공 - topic: {}, key: {}, offset: {}",
                                    topic, key, result.getRecordMetadata().offset());
                        }
                    });
        } catch (JacksonException e) {
            log.error("[Kafka] 직렬화 실패 - topic: {}, key: {}, payloadType: {}", topic, key, payload.getClass().getSimpleName(), e);
            throw new GeneralException(ErrorStatus.KAFKA_PUBLISH_FAILED);
        }
    }
}
