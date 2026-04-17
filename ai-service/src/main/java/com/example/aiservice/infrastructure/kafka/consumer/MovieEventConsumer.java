package com.example.aiservice.infrastructure.kafka.consumer;

import com.example.aiservice.application.usecase.InteractionUseCase;
import com.example.aiservice.application.usecase.MovieSyncUseCase;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieUnlikedMessage;
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

    @KafkaListener(topics = "movie.created", groupId = "ai-service")
    public void consumeMovieCreated(String message) {
        log.info("[Kafka] movie.created 수신 - payload: {}", message);
        MovieCreatedMessage msg = objectMapper.readValue(message, MovieCreatedMessage.class);
        movieSyncUseCase.handleMovieCreated(msg);
    }

    // todo : movie.updated 도 해야 함

    @KafkaListener(topics = "movie.deleted", groupId = "ai-service")
    public void consumeMovieDeleted(String message) {
        log.info("[Kafka] movie.deleted 수신 - payload: {}", message);
        MovieDeletedMessage msg = objectMapper.readValue(message, MovieDeletedMessage.class);
        movieSyncUseCase.handleMovieDeleted(msg);
    }

    @KafkaListener(topics = "movie.liked", groupId = "ai-service")
    public void consumeMovieLiked(String message) {
        log.info("[Kafka] movie.liked 수신 - payload: {}", message);
        MovieLikedMessage msg = objectMapper.readValue(message, MovieLikedMessage.class);
        interactionUseCase.handleMovieLiked(msg);
    }

    @KafkaListener(topics = "movie.unliked", groupId = "ai-service")
    public void consumeMovieUnliked(String message) {
        log.info("[Kafka] movie.unliked 수신 - payload: {}", message);
        MovieUnlikedMessage msg = objectMapper.readValue(message, MovieUnlikedMessage.class);
        interactionUseCase.handleMovieUnliked(msg);
    }
}
