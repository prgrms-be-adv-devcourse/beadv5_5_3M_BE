package com.example.aiservice.infrastructure.kafka.consumer;

import com.example.aiservice.application.usecase.CreatorUseCase;
import com.example.aiservice.infrastructure.kafka.dto.consume.CreatorCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.CreatorUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorEventConsumer {

    private final ObjectMapper objectMapper;
    private final CreatorUseCase creatorUseCase;

    @KafkaListener(topics = "creator.created", groupId = "ai-service")
    public void consumeCreatorCreated(String message) {
        log.info("[Kafka] creator.created 수신 - payload: {}", message);
        CreatorCreatedMessage msg = objectMapper.readValue(message, CreatorCreatedMessage.class);
        creatorUseCase.handleCreatorCreated(msg);
        log.info("[Kafka] creator.created 처리 완료 - creatorId: {}, nickname: {}", msg.creatorId(), msg.nickname());
    }

    @KafkaListener(topics = "creator.updated", groupId = "ai-service")
    public void consumeCreatorUpdated(String message) {
        log.info("[Kafka] creator.updated 수신 - payload: {}", message);
        CreatorUpdatedMessage msg = objectMapper.readValue(message, CreatorUpdatedMessage.class);
        creatorUseCase.handleCreatorUpdated(msg);
        log.info("[Kafka] creator.updated 처리 완료 - creatorId: {}, nickname: {}", msg.creatorId(), msg.nickname());
    }
}
