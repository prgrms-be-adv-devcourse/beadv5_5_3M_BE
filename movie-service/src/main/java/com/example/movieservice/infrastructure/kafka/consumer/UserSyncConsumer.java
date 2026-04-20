package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.domain.model.UserSync;
import com.example.movieservice.domain.repository.ReviewAuthorizationRepository;
import com.example.movieservice.domain.repository.UserSyncRepository;
import com.example.movieservice.infrastructure.kafka.dto.consume.UserCreatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.UserDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.UserUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncConsumer {

    private final ObjectMapper objectMapper;
    private final UserSyncRepository userSyncRepository;
    private final ReviewAuthorizationRepository reviewAuthorizationRepository;

    @KafkaListener(topics = "user.created", groupId = "movie-service")
    @Transactional
    public void consumeUserCreated(String message) {
        try {
            UserCreatedMessage msg = objectMapper.readValue(message, UserCreatedMessage.class);
            if (userSyncRepository.existsById(msg.userId())) {
                log.warn("[Kafka] user.created - 이미 존재하는 유저, skip - userId: {}", msg.userId());
                return;
            }
            UserSync userSync = UserSync.builder()
                    .userId(msg.userId())
                    .nickname(msg.nickname())
                    .url(msg.profileUrl())
                    .build();
            userSyncRepository.save(userSync);
            log.info("[Kafka] user.created 처리 완료 - userId: {}", msg.userId());
        } catch (Exception e) {
            log.error("[Kafka] user.created 처리 실패 - payload: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "user.updated", groupId = "movie-service")
    @Transactional
    public void consumeUserUpdated(String message) {
        try {
            UserUpdatedMessage msg = objectMapper.readValue(message, UserUpdatedMessage.class);
            userSyncRepository.findById(msg.userId()).ifPresentOrElse(
                    user -> {
                        user.update(msg.nickname(), msg.profileUrl());
                        userSyncRepository.save(user);
                        log.info("[Kafka] user.updated 처리 완료 - userId: {}", msg.userId());
                    },
                    () -> log.warn("[Kafka] user.updated - 대상 유저 없음, skip - userId: {}", msg.userId())
            );
        } catch (Exception e) {
            log.error("[Kafka] user.updated 처리 실패 - payload: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "user.deleted", groupId = "movie-service")
    @Transactional
    public void consumeUserDeleted(String message) {
        try {
            UserDeletedMessage msg = objectMapper.readValue(message, UserDeletedMessage.class);
            reviewAuthorizationRepository.deleteByUserId(msg.userId());
            userSyncRepository.deleteById(msg.userId());
            log.info("[Kafka] user.deleted 처리 완료 - userId: {}", msg.userId());
        } catch (Exception e) {
            log.error("[Kafka] user.deleted 처리 실패 - payload: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}