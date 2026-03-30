package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.application.usecase.ReviewUseCase;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewUpdatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.ReviewWrittenMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventConsumer {

    private final ReviewUseCase reviewUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "review.written", groupId = "movie-service")
    public void consumeReviewWritten(String message) {
        log.info("[Kafka] review.written 수신 - payload: {}", message);
        ReviewWrittenMessage msg = objectMapper.readValue(message, ReviewWrittenMessage.class);
        reviewUseCase.handleReviewWritten(msg);
        log.info("[Kafka] review.written 처리 완료 - reviewId: {}, movieId: {}", msg.reviewId(), msg.movieId());
    }

    @KafkaListener(topics = "review.updated", groupId = "movie-service")
    public void consumeReviewUpdated(String message) {
        log.info("[Kafka] review.updated 수신 - payload: {}", message);
        ReviewUpdatedMessage msg = objectMapper.readValue(message, ReviewUpdatedMessage.class);
        reviewUseCase.handleReviewUpdated(msg);
        log.info("[Kafka] review.updated 처리 완료 - reviewId: {}", msg.reviewId());
    }

    @KafkaListener(topics = "review.deleted", groupId = "movie-service")
    public void consumeReviewDeleted(String message) {
        log.info("[Kafka] review.deleted 수신 - payload: {}", message);
        ReviewDeletedMessage msg = objectMapper.readValue(message, ReviewDeletedMessage.class);
        reviewUseCase.handleReviewDeleted(msg);
        log.info("[Kafka] review.deleted 처리 완료 - reviewId: {}", msg.reviewId());
    }
}