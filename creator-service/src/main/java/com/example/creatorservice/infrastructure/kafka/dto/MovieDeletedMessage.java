package com.example.creatorservice.infrastructure.kafka.dto;

// topic: movie.deleted
// receiver: movie-service (ES 제거)
public record MovieDeletedMessage(
        Long movieId
) {}