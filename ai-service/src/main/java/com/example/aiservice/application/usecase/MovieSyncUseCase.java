package com.example.aiservice.application.usecase;

import com.example.aiservice.infrastructure.kafka.dto.consume.MovieCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieUpdatedMessage;

public interface MovieSyncUseCase {

    void handleMovieCreated(MovieCreatedMessage message);

    void handleMovieUpdated(MovieUpdatedMessage message);

    void handleMovieDeleted(MovieDeletedMessage message);
}
