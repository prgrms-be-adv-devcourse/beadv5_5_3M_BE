package com.example.ticketservice.infrastructure.messaging.producer;

import com.example.ticketservice.infrastructure.util.KafkaMessageUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaBulkEventPublisher extends AbstractKafkaPublisher {

    public KafkaBulkEventPublisher(
            @Qualifier("bulkKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            KafkaMessageUtil kafkaMessageUtil) {
        super(kafkaTemplate, kafkaMessageUtil, "대량");
    }
}
