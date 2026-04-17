package com.example.aiservice.application.service;

import com.example.aiservice.application.usecase.InteractionUseCase;
import com.example.aiservice.domain.model.UserInteractionHistoryId;
import com.example.aiservice.domain.model.enums.InteractionType;
import com.example.aiservice.domain.repository.UserInteractionHistoryRepository;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieUnlikedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InteractionService implements InteractionUseCase {

    private final UserInteractionHistoryRepository userInteractionHistoryRepository;

    @Override
    @Transactional
    public void handleMovieLiked(MovieLikedMessage msg) {
        // ON CONFLICT DO NOTHING: 동일 (userId, movieId, 'LIKE') 행이 이미 있으면 무시
        userInteractionHistoryRepository.insertOnConflictDoNothing(
                msg.userId(), msg.movieId(), InteractionType.LIKE.name(), LocalDateTime.now()
        );
        log.info("[Kafka] movie.liked 처리 완료 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
    }

    @Override
    @Transactional
    public void handleMovieUnliked(MovieUnlikedMessage msg) {
        UserInteractionHistoryId id = new UserInteractionHistoryId(
                msg.userId(), msg.movieId(), InteractionType.LIKE
        );
        userInteractionHistoryRepository.deleteById(id);
        log.info("[Kafka] movie.unliked 처리 완료 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
    }
}
