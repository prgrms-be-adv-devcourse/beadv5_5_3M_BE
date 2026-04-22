package com.example.movieservice.infrastructure.kafka.dto.consume;

// topic: movie.deleted
// producer: creator-service (영화 삭제 시)
public record MovieDeletedMessage(
        Long movieId
) {}