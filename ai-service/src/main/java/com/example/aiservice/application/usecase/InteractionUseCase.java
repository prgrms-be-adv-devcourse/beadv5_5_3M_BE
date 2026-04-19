package com.example.aiservice.application.usecase;

import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;

public interface InteractionUseCase {

    void handleMovieLiked(MovieLikedMessage message);
}
