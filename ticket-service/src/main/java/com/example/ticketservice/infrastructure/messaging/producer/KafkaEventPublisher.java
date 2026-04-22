package com.example.ticketservice.infrastructure.messaging.producer;

import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Primary
@Component
public class KafkaEventPublisher extends AbstractKafkaPublisher {

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               KafkaMessageUtil kafkaMessageUtil) {
        super(kafkaTemplate, kafkaMessageUtil, "단건");
    }
}
