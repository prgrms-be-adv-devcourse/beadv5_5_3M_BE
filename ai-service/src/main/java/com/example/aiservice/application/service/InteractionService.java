package com.example.aiservice.application.service;

import com.example.aiservice.application.usecase.InteractionUseCase;
import com.example.aiservice.domain.model.UserInteractionHistory;
import com.example.aiservice.domain.model.UserPreference;
import com.example.aiservice.domain.model.enums.InteractionType;
import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import com.example.aiservice.domain.repository.MovieStatisticsRepository;
import com.example.aiservice.domain.repository.UserInteractionHistoryRepository;
import com.example.aiservice.domain.repository.UserPreferenceRepository;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.TicketReviewAuthorizedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InteractionService implements InteractionUseCase {

    private final UserInteractionHistoryRepository userInteractionHistoryRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final MovieEmbeddedRepository movieEmbeddedRepository;
    private final MovieStatisticsRepository movieStatisticsRepository;

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

    @Override
    @Transactional
    public void handleTicketWatchCompleted(TicketReviewAuthorizedMessage msg) {
        // movie 미동기화 → RuntimeException 전파 → @RetryableTopic 재시도
        if (!movieEmbeddedRepository.existsById(msg.movieId())) {
            throw new RuntimeException("Movie not synced yet: movieId=" + msg.movieId());
        }

        // 멱등성: 동일 scheduleId 중복 이벤트 skip
        if (userInteractionHistoryRepository.existsByUserIdAndMovieIdAndInteractionTypeAndScheduleId(
                msg.userId(), msg.movieId(), InteractionType.WATCH, msg.scheduleId())) {
            log.warn("[Kafka] ticket.review.authorized - 중복 이벤트 skip - userId: {}, movieId: {}, scheduleId: {}",
                    msg.userId(), msg.movieId(), msg.scheduleId());
            return;
        }

        // 첫 시청 여부 확인 (movie_statistics.watch_count는 unique viewer 카운트)
        boolean isFirstWatch = !userInteractionHistoryRepository.existsByUserIdAndMovieIdAndInteractionType(
                msg.userId(), msg.movieId(), InteractionType.WATCH);

        // user_interaction_history INSERT
        userInteractionHistoryRepository.save(UserInteractionHistory.builder()
                .userId(msg.userId())
                .movieId(msg.movieId())
                .interactionType(InteractionType.WATCH)
                .scheduleId(msg.scheduleId())
                .createdAt(LocalDateTime.now())
                .build());

        // movie_statistics UPDATE (user_preference 없으면 통계만 skip, K-Means 기록은 정상 저장)
        Optional<UserPreference> prefOpt = userPreferenceRepository.findById(msg.userId());
        if (prefOpt.isEmpty()) {
            log.warn("[Kafka] ticket.review.authorized - user_preference 없음, 통계 UPDATE skip - userId: {}", msg.userId());
            return;
        }
        UserPreference pref = prefOpt.get();

        if (isFirstWatch) {
            movieStatisticsRepository.incrementWatchCount(msg.movieId(), pref.getAgeGroup(), pref.getGender());
        }
        userPreferenceRepository.incrementWatchCount(msg.userId());

        log.info("[Kafka] ticket.review.authorized 처리 완료 - userId: {}, movieId: {}, scheduleId: {}, isFirstWatch: {}",
                msg.userId(), msg.movieId(), msg.scheduleId(), isFirstWatch);
    }
}
