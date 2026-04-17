package com.example.aiservice.application.usecase;

import com.example.aiservice.infrastructure.kafka.dto.consume.MovieCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.MovieDeletedMessage;

public interface MovieSyncUseCase {

    void handleMovieCreated(MovieCreatedMessage message);

    void handleMovieDeleted(MovieDeletedMessage message);
}
