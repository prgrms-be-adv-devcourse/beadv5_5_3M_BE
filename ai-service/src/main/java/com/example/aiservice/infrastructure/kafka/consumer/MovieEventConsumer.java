package com.example.aiservice.infrastructure.kafka.consumer;

import com.example.aiservice.application.usecase.InteractionUseCase;
import com.example.aiservice.application.usecase.MovieSyncUseCase;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieEventConsumer {

    private final ObjectMapper objectMapper;
    private final MovieSyncUseCase movieSyncUseCase;
    private final InteractionUseCase interactionUseCase;

    @KafkaListener(topics = "movie.ai.created", groupId = "ai-service")
    public void consumeMovieCreated(String message) {
        log.info("[Kafka] movie.created 수신 - payload: {}", message);
        try {
            MovieCreatedMessage msg = objectMapper.readValue(message, MovieCreatedMessage.class);
            movieSyncUseCase.handleMovieCreated(msg);
        } catch (Exception e) {
            log.warn("[Kafka] movie.created 처리 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
        }
    }

    @KafkaListener(topics = "movie.ai.updated", groupId = "ai-service")
    public void consumeMovieUpdated(String message) {
        log.info("[Kafka] movie.updated 수신 - payload: {}", message);
        try {
            MovieUpdatedMessage msg = objectMapper.readValue(message, MovieUpdatedMessage.class);
            movieSyncUseCase.handleMovieUpdated(msg);
        } catch (Exception e) {
            log.warn("[Kafka] movie.updated 처리 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
        }
    }

    @KafkaListener(topics = "movie.deleted", groupId = "ai-service")
    public void consumeMovieDeleted(String message) {
        log.info("[Kafka] movie.deleted 수신 - payload: {}", message);
        try {
            MovieDeletedMessage msg = objectMapper.readValue(message, MovieDeletedMessage.class);
            movieSyncUseCase.handleMovieDeleted(msg);
        } catch (Exception e) {
            log.warn("[Kafka] movie.deleted 처리 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
        }
    }

    @KafkaListener(topics = "movie.liked", groupId = "ai-service")
    public void consumeMovieLiked(String message) {
        log.info("[Kafka] movie.liked 수신 - payload: {}", message);
        try {
            MovieLikedMessage msg = objectMapper.readValue(message, MovieLikedMessage.class);
            interactionUseCase.handleMovieLiked(msg);
        } catch (Exception e) {
            log.warn("[Kafka] movie.liked 처리 실패, 메시지 skip - payload: {}, error: {}", message, e.getMessage());
        }
    }
}
