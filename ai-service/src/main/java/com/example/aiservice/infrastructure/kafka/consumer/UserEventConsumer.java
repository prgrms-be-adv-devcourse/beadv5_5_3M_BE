package com.example.aiservice.infrastructure.kafka.consumer;

import com.example.aiservice.application.usecase.UserPreferenceUseCase;
import com.example.aiservice.infrastructure.kafka.dto.consume.UserCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.UserDeletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ObjectMapper objectMapper;
    private final UserPreferenceUseCase userPreferenceUseCase;

    @KafkaListener(topics = "user.created", groupId = "ai-service")
    public void consumeUserCreated(String message) {
        log.info("[Kafka] user.created 수신 - payload: {}", message);
        try {
            UserCreatedMessage msg = objectMapper.readValue(message, UserCreatedMessage.class);
            userPreferenceUseCase.handleUserCreated(msg);
            log.info("[Kafka] user.created 처리 완료 - userId: {}", msg.userId());
        } catch (Exception e) {
            log.warn("[Kafka] user.created 처리 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
        }
    }

    @KafkaListener(topics = "user.deleted", groupId = "ai-service")
    public void consumeUserDeleted(String message) {
        log.info("[Kafka] user.deleted 수신 - payload: {}", message);
        try {
            UserDeletedMessage msg = objectMapper.readValue(message, UserDeletedMessage.class);
            userPreferenceUseCase.handleUserDeleted(msg);
        } catch (Exception e) {
            log.warn("[Kafka] user.deleted 처리 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
        }
    }
}
