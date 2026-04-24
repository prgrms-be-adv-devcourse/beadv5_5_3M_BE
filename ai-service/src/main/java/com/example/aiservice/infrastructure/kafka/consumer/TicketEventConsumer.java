package com.example.aiservice.infrastructure.kafka.consumer;

import com.example.aiservice.application.usecase.InteractionUseCase;
import com.example.aiservice.infrastructure.kafka.dto.consume.TicketReviewAuthorizedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventConsumer {

    private final ObjectMapper objectMapper;
    private final InteractionUseCase interactionUseCase;

    @RetryableTopic(
            attempts = "4",   // 1회 원본 + 3회 재시도
            backOff = @BackOff(delay = 30_000, multiplier = 2.0, maxDelay = 120_000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "ticket.review.authorized", groupId = "ai-service")
    public void consumeTicketReviewAuthorized(String message) {
        log.info("[Kafka] ticket.review.authorized 수신 - payload: {}", message);
        TicketReviewAuthorizedMessage msg;
        try {
            msg = objectMapper.readValue(message, TicketReviewAuthorizedMessage.class);
        } catch (Exception e) {
            log.warn("[Kafka] ticket.review.authorized 파싱 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
            return;  // 파싱 에러는 예외 전파 없이 종료 → retry 없음
        }
        interactionUseCase.handleTicketWatchCompleted(msg);  // movie 미동기화 시 RuntimeException → retry 트리거
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[Kafka-DLT] ticket.review.authorized - 재시도 소진, DLT 도착 - payload: {}", message);
    }
}
