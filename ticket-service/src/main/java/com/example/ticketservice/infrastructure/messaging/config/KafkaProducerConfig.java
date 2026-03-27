package com.example.ticketservice.infrastructure.messaging.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ─────────────────────────────────────────────
    // 공통 기본 설정 (YAML의 StringSerializer와 일치)
    // ─────────────────────────────────────────────
    private Map<String, Object> baseProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Value: KafkaMessageUtil.serialize()로 미리 JSON String 변환 후 StringSerializer 전송
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }

    // ─────────────────────────────────────────────
    // 일반 단건 발행용 ProducerFactory / KafkaTemplate
    // 사용 시점: 예약, 취소, 리뷰 권한 등 실시간 단건 이벤트
    // ─────────────────────────────────────────────
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerProps());
    }

    @Primary // 두 KafkaTemplate 빈 중 기본 빈으로 지정
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ─────────────────────────────────────────────
    // 대량 배치 발행용 ProducerFactory / KafkaTemplate
    // 사용 시점: 일일 정산처럼 한 번에 N건을 묶어 보내는 배치 작업
    //   - batch.size  64KB : 배치 버퍼를 키워 묶음 전송 효율 향상
    //   - linger.ms   20   : 최대 20 ms 대기 후 배치 전송 (처리량 ↑, 지연 허용)
    //   - compression lz4   : 네트워크 트래픽 절감 (kafka-clients 내장, 추가 의존성 불필요)
    // ─────────────────────────────────────────────
    @Bean
    public ProducerFactory<String, String> bulkProducerFactory() {
        Map<String, Object> props = baseProducerProps();
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 64 * 1024);   // 64 KB
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> bulkKafkaTemplate(
            ProducerFactory<String, String> bulkProducerFactory) {
        return new KafkaTemplate<>(bulkProducerFactory);
    }
}