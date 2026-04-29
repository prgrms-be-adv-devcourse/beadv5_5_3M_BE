package com.example.creatorservice.infrastructure.kafka.listener;

import com.example.creatorservice.infrastructure.kafka.MovieEventPublisher;
import com.example.creatorservice.infrastructure.kafka.event.MovieAiCreatedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieAiUpdatedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieDeletedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieFileDeleteEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieUpdatedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieUploadedEvent;
import com.example.creatorservice.infrastructure.kafka.event.MovieVisibilityChangedEvent;
import com.example.creatorservice.infrastructure.kafka.event.ScheduleConfirmedEvent;
import com.example.creatorservice.infrastructure.storage.FileStorageService;
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
    private final FileStorageService fileStorageService;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieUploaded(MovieUploadedEvent event) {
        log.info("[Movie Event] movie.uploaded 발행 - movieId: {}", event.message().movieId());
        movieEventPublisher.publishMovieUploaded(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieUpdated(MovieUpdatedEvent event) {
        log.info("[Movie Event] movie.updated 발행 - movieId: {}", event.message().movieId());
        movieEventPublisher.publishMovieUpdated(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieDeleted(MovieDeletedEvent event) {
        log.info("[Movie Event] movie.deleted 발행 - movieId: {}", event.message().movieId());
        movieEventPublisher.publishMovieDeleted(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieVisibilityChanged(MovieVisibilityChangedEvent event) {
        log.info("[Movie Event] movie.visibility 발행 - movieId: {}", event.message().movieId());
        movieEventPublisher.publishMovieVisibilityChanged(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleConfirmed(ScheduleConfirmedEvent event) {
        log.info("[Schedule Event] movie.schedule.confirmed 발행 - scheduleId: {}", event.message().scheduleId());
        movieEventPublisher.publishScheduleConfirmed(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovieFileDelete(MovieFileDeleteEvent event) {
        if (event.imageUrl() != null) {
            fileStorageService.delete(event.imageUrl());
        }
        if (event.videoUrl() != null) {
            fileStorageService.delete(event.videoUrl());
        }
    }
}