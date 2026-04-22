package com.example.streamingservice.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka producer/consumer 는 application.yaml 의 spring.kafka.* 로 auto-configure.
 * @EnableKafka 는 {@link com.example.streamingservice.StreamingServiceApplication} 에 선언돼 있고,
 * @RetryableTopic 은 각 consumer 메서드에 직접 부착한다 (MESSAGE-SCHEMAS.md §4).
 */
@Configuration
public class KafkaConfig {
}