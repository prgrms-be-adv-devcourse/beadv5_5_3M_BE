package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.application.usecase.CreatorUseCase;
import com.example.movieservice.infrastructure.kafka.dto.consume.CreatorCreatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.CreatorUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorEventConsumer {

    private final ObjectMapper objectMapper;
    private final CreatorUseCase creatorUseCase;

    @KafkaListener(topics = "creator.created", groupId = "movie-service")
    public void consumeCreatorCreated(String message) {
        CreatorCreatedMessage msg;
        try {
            msg = objectMapper.readValue(message, CreatorCreatedMessage.class);
        } catch (JacksonException e) {
            // poison pill — 파싱 불가 메시지는 skip하여 무한 retry 방지
            // TODO: creator.created.DLQ 토픽으로 이동 권장
            log.error("[Kafka] creator.created 파싱 실패(poison pill), skip - payload: {}", message, e);
            return;
        }
        log.info("[Kafka] creator.created 수신 - creatorId: {}, nickname: {}", msg.creatorId(), msg.nickname());
        // 비즈니스 실패는 예외 전파 → Kafka가 재시도
        creatorUseCase.handleCreatorCreated(msg);
        log.info("[Kafka] creator.created 처리 완료 - creatorId: {}", msg.creatorId());
    }

    @KafkaListener(topics = "creator.updated", groupId = "movie-service")
    public void consumeCreatorUpdated(String message) {
        CreatorUpdatedMessage msg;
        try {
            msg = objectMapper.readValue(message, CreatorUpdatedMessage.class);
        } catch (JacksonException e) {
            // poison pill — 파싱 불가 메시지는 skip하여 무한 retry 방지
            // TODO: creator.updated.DLQ 토픽으로 이동 권장
            log.error("[Kafka] creator.updated 파싱 실패(poison pill), skip - payload: {}", message, e);
            return;
        }
        log.info("[Kafka] creator.updated 수신 - creatorId: {}, nickname: {}", msg.creatorId(), msg.nickname());
        // 비즈니스 실패는 예외 전파 → Kafka가 재시도
        creatorUseCase.handleCreatorUpdated(msg);
        log.info("[Kafka] creator.updated 처리 완료 - creatorId: {}", msg.creatorId());
    }

}
