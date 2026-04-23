package com.example.movieservice.infrastructure.kafka.dto.consume;

// topic: movie.visibility
// producer: creator-service (최초 PUBLIC 이후 visibility 변경 시마다 발행)
public record MovieVisibilityChangedMessage(
        Long movieId,
        String visibility  // "PUBLIC" | "PRIVATE"
) {}