package com.example.creatorservice.infrastructure.kafka.listener;

import com.example.creatorservice.infrastructure.kafka.MovieEventPublisher;
import com.example.creatorservice.infrastructure.kafka.event.MovieAiCreatedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieAiUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieAiEventListener {

    private final MovieEventPublisher movieEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieAiCreated(MovieAiCreatedEvent event) {
        log.info("[AI Event] movie.ai.created 발행 - movieId: {}", event.message().movieId());
        movieEventPublisher.publishMovieAiCreated(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieAiUpdated(MovieAiUpdatedEvent event) {
        log.info("[AI Event] movie.ai.updated 발행 - movieId: {}", event.message().movieId());
        movieEventPublisher.publishMovieAiUpdated(event.message());
    }
}