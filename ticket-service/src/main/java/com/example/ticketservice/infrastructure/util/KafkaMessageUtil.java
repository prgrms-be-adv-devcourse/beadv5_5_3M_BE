package com.example.ticketservice.infrastructure.util;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageUtil {

    private final ObjectMapper objectMapper;

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Kafka 메시지 직렬화 실패: " + e.getMessage(), e);
        }
    }

    public <T> T deserialize(String message, Class<T> t) {
        try {
            return objectMapper.readValue(message, t);
        } catch (JacksonException e) {
            log.error("Kafka 메시지 역직렬화 실패 - payload: {}", message, e);
            throw new IllegalArgumentException("Kafka 메시지 역직렬화 실패: " + e.getMessage(), e);
        }
    }
}