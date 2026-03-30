package com.example.ticketservice.infrastructure.messaging.producer;


import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Primary // @Qualifier 없이 주입되는 곳의 기본 발행자 (단건 이벤트용)
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisherPort {

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