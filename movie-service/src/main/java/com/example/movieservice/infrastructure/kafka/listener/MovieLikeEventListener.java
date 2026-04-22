package com.example.movieservice.infrastructure.kafka.listener;

import com.example.movieservice.application.event.EventPublisher;
import com.example.movieservice.application.event.MovieLikedEvent;
import com.example.movieservice.infrastructure.kafka.dto.publish.MovieLikedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieLikeEventListener {

    private final EventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMovieLiked(MovieLikedEvent event) {
        log.info("[Kafka] movie.liked 발행 시도 - userId: {}, movieId: {}, action: {}",
                event.userId(), event.movieId(), event.action());
        try {
            eventPublisher.publish(
                    "movie.liked",
                    event.movieId().toString(),
                    new MovieLikedMessage(event.userId(), event.movieId(), event.action())
            );
        } catch (Exception e) {
            log.error("[Kafka] movie.liked 발행 실패 - userId: {}, movieId: {}", event.userId(), event.movieId(), e);
        }
    }
}