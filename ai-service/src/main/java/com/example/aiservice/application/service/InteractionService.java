package com.example.aiservice.application.service;

import com.example.aiservice.application.usecase.InteractionUseCase;
import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.enums.InteractionType;
import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import com.example.aiservice.domain.repository.UserInteractionHistoryRepository;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;
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
    private final MovieEmbeddedRepository movieEmbeddedRepository;

    @Override
    @Transactional
    public void handleMovieLiked(MovieLikedMessage msg) {
        if ("LIKED".equals(msg.action())) {
            if (!movieEmbeddedRepository.existsById(msg.movieId())) {
                log.warn("[Kafka] movie.liked(LIKED) - 존재하지 않는 movieId: {}", msg.movieId());
            }
            if (userInteractionHistoryRepository.existsByUserIdAndMovieIdAndInteractionType(
                    msg.userId(), msg.movieId(), InteractionType.LIKE)) {
                log.warn("[Kafka] movie.liked(LIKED) - 이미 좋아요한 영화 skip - userId: {}, movieId: {}", msg.userId(), msg.movieId());
                return;
            }
            userInteractionHistoryRepository.save(UserInteractionHistory.builder()
                    .userId(msg.userId())
                    .movieId(msg.movieId())
                    .interactionType(InteractionType.LIKE)
                    .scheduleId(null)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("[Kafka] movie.liked(LIKED) 처리 완료 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
        } else if ("UNLIKED".equals(msg.action())) {
            if (!userInteractionHistoryRepository.existsByUserIdAndMovieIdAndInteractionType(
                    msg.userId(), msg.movieId(), InteractionType.LIKE)) {
                log.warn("[Kafka] movie.liked(UNLIKED) - 존재하지 않는 interaction skip - userId: {}, movieId: {}", msg.userId(), msg.movieId());
                return;
            }
            userInteractionHistoryRepository.deleteByUserIdAndMovieIdAndInteractionType(
                    msg.userId(), msg.movieId(), InteractionType.LIKE);
            log.info("[Kafka] movie.liked(UNLIKED) 처리 완료 - userId: {}, movieId: {}", msg.userId(), msg.movieId());
        } else {
            log.warn("[Kafka] movie.liked - 알 수 없는 action: {}", msg.action());
        }
    }
}
