package com.example.aiservice.application.service;

import com.example.aiservice.application.usecase.UserPreferenceUseCase;
import com.example.aiservice.domain.model.UserPreference;
import com.example.aiservice.domain.model.enums.Gender;
import com.example.aiservice.domain.repository.UserPreferenceRepository;
import com.example.aiservice.infrastructure.kafka.dto.consume.UserCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.UserDeletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService implements UserPreferenceUseCase {

    private final UserPreferenceRepository userPreferenceRepository;

    @Override
    @Transactional
    public void handleUserCreated(UserCreatedMessage msg) {
        if (userPreferenceRepository.existsById(msg.userId())) {
            log.warn("[Kafka] 중복 메시지 skip - userId: {}", msg.userId());
            return;
        }
        UserPreference userPreference = UserPreference.builder()
                .userId(msg.userId())
                .ageGroup(msg.ageGroup())
                .gender(Gender.valueOf(msg.gender()))
                .cluster(null)
                .watchCount(0)
                .updatedAt(LocalDateTime.now())
                .explorationClickRate(0.0)
                .epsilon(0.0)
                .build();

        userPreferenceRepository.save(userPreference);
    }

    @Override
    @Transactional
    public void handleUserDeleted(UserDeletedMessage msg) {
        // TODO(movie-sync): user_interaction_history DELETE WHERE user_id = ?
        // TODO(movie-sync): recommended_movie DELETE WHERE user_id = ?
        // TODO(recommendation-api): recommended_log DELETE WHERE user_id = ?
        userPreferenceRepository.deleteById(msg.userId());
        log.info("[Kafka] user.deleted 처리 완료 - userId: {}", msg.userId());
    }
}
