package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieUpdatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieUploadedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieVisibilityChangedMessage;
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

    @KafkaListener(topics = "movie.uploaded", groupId = "movie-service")
    public void consumeMovieUploaded(String message) {
        try {
            MovieUploadedMessage msg = objectMapper.readValue(message, MovieUploadedMessage.class);
            log.info("[Kafka] movie.uploaded 수신 - movieId: {}, title: {}, creatorId: {}",
                    msg.movieId(), msg.title(), msg.creatorId());
        } catch (Exception e) {
            log.error("[Kafka] movie.uploaded 파싱 실패 - payload: {}", message, e);
        }
    }

    @KafkaListener(topics = "movie.updated", groupId = "movie-service")
    public void consumeMovieUpdated(String message) {
        try {
            MovieUpdatedMessage msg = objectMapper.readValue(message, MovieUpdatedMessage.class);
            log.info("[Kafka] movie.updated 수신 - movieId: {}, title: {}",
                    msg.movieId(), msg.title());
        } catch (Exception e) {
            log.error("[Kafka] movie.updated 파싱 실패 - payload: {}", message, e);
        }
    }

    @KafkaListener(topics = "movie.deleted", groupId = "movie-service")
    public void consumeMovieDeleted(String message) {
        try {
            MovieDeletedMessage msg = objectMapper.readValue(message, MovieDeletedMessage.class);
            log.info("[Kafka] movie.deleted 수신 - movieId: {}", msg.movieId());
        } catch (Exception e) {
            log.error("[Kafka] movie.deleted 파싱 실패 - payload: {}", message, e);
        }
    }

    @KafkaListener(topics = "movie.visibility", groupId = "movie-service")
    public void consumeMovieVisibilityChanged(String message) {
        try {
            MovieVisibilityChangedMessage msg = objectMapper.readValue(message, MovieVisibilityChangedMessage.class);
            log.info("[Kafka] movie.visibility 수신 - movieId: {}, visibility: {}", msg.movieId(), msg.visibility());
        } catch (Exception e) {
            log.error("[Kafka] movie.visibility 파싱 실패 - payload: {}", message, e);
        }
    }
}