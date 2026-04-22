package com.example.movieservice.infrastructure.kafka.consumer;

import com.example.movieservice.application.usecase.MovieSearchUseCase;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieVisibilityChangedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieUpdatedMessage;
import com.example.movieservice.infrastructure.kafka.dto.consume.MovieUploadedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 이벤트를 수신하여 ES 색인을 자동 동기화하는 Consumer.
 *
 * Creator 서비스가 발행하는 토픽 (팀 표준):
 *   - movie.uploaded    → ES 색인 생성
 *   - movie.updated     → ES 색인 업데이트 (제목/설명/카테고리)
 *   - movie.deleted     → ES 색인 제거
 *   - movie.visibility  → ES visibility 업데이트
 *
 * 같은 토픽을 movie-service 팀의 MovieEventConsumer(groupId: movie-service)도 구독하지만,
 * 본 Consumer 는 groupId 를 "movie-search-service" 로 분리하여 독립적으로 전체 메시지를 수신한다.
 *
 * MSA 구조: Creator 서비스 DB에 직접 접근 불가
 * → 메시지에 포함된 데이터로 ES 색인을 생성/업데이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movie.elasticsearch", name = "enabled", havingValue = "true")
public class MovieIndexEventConsumer {

    private final MovieSearchUseCase movieSearchUseCase;
    private final ObjectMapper objectMapper;

    /**
     * 새 영화 등록 → ES 색인 생성
     */
    @KafkaListener(topics = "movie.uploaded", groupId = "movie-search-service")
    public void consumeMovieUploaded(String message) {
        log.info("[Kafka → ES] movie.uploaded 수신: {}", message);
        MovieUploadedMessage msg = parseMessage(message, "movie.uploaded", MovieUploadedMessage.class);
        if (msg == null) return;

        List<Long> categoryIds = new ArrayList<>();
        List<String> categoryNames = new ArrayList<>();
        if (msg.categories() != null) {
            for (MovieUploadedMessage.CategoryInfo c : msg.categories()) {
                categoryIds.add(c.categoryId());
                categoryNames.add(c.name());
            }
        }

        movieSearchUseCase.indexMovieFromMessage(
                msg.movieId(), msg.title(), msg.description(),
                msg.creatorId() != null ? msg.creatorId().toString() : null,
                msg.creatorNickname(),
                categoryIds, categoryNames);
        log.info("[Kafka → ES] 영화 색인 생성 완료 - movieId: {}", msg.movieId());
    }

    /**
     * 영화 정보 수정 → ES 색인 업데이트 (제목/설명/카테고리)
     */
    @KafkaListener(topics = "movie.updated", groupId = "movie-search-service")
    public void consumeMovieUpdated(String message) {
        log.info("[Kafka → ES] movie.updated 수신: {}", message);
        MovieUpdatedMessage msg = parseMessage(message, "movie.updated", MovieUpdatedMessage.class);
        if (msg == null) return;

        List<Long> categoryIds = null;
        List<String> categoryNames = null;
        if (msg.categories() != null) {
            categoryIds = new ArrayList<>();
            categoryNames = new ArrayList<>();
            for (MovieUpdatedMessage.CategoryInfo c : msg.categories()) {
                categoryIds.add(c.categoryId());
                categoryNames.add(c.name());
            }
        }

        movieSearchUseCase.updateMovieIndex(msg.movieId(), msg.title(), msg.description(),
                categoryIds, categoryNames);
        log.info("[Kafka → ES] 영화 색인 업데이트 완료 - movieId: {}", msg.movieId());
    }

    /**
     * 영화 삭제 → ES 색인 제거
     */
    @KafkaListener(topics = "movie.deleted", groupId = "movie-search-service")
    public void consumeMovieDeleted(String message) {
        log.info("[Kafka → ES] movie.deleted 수신: {}", message);
        MovieDeletedMessage msg = parseMessage(message, "movie.deleted", MovieDeletedMessage.class);
        if (msg == null) return;

        movieSearchUseCase.deleteMovieIndex(msg.movieId());
        log.info("[Kafka → ES] 영화 색인 삭제 완료 - movieId: {}", msg.movieId());
    }

    /**
     * 공개 상태 변경 → ES visibility 업데이트
     */
    @KafkaListener(topics = "movie.visibility", groupId = "movie-search-service")
    public void consumeMovieVisibilityChanged(String message) {
        log.info("[Kafka → ES] movie.visibility 수신: {}", message);
        MovieVisibilityChangedMessage msg = parseMessage(message, "movie.visibility", MovieVisibilityChangedMessage.class);
        if (msg == null) return;

        movieSearchUseCase.updateMovieVisibility(msg.movieId(), msg.visibility());
        log.info("[Kafka → ES] 영화 공개 상태 변경 완료 - movieId: {}, visibility: {}", msg.movieId(), msg.visibility());
    }

    /**
     * JSON 파싱 헬퍼. 파싱 실패는 비복구성이므로 로그 후 null 반환 (스킵).
     */
    private <T> T parseMessage(String message, String topic, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (Exception e) {
            log.error("[Kafka → ES] {} JSON 파싱 실패 (스킵) - payload: {}", topic, message, e);
            return null;
        }
    }
}
