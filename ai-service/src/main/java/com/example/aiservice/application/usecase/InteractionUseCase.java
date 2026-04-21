package com.example.aiservice.application.usecase;

import com.example.aiservice.infrastructure.kafka.dto.consume.MovieLikedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.TicketReviewAuthorizedMessage;

public interface InteractionUseCase {

    void handleMovieLiked(MovieLikedMessage message);

    void handleTicketWatchCompleted(TicketReviewAuthorizedMessage message);
}
