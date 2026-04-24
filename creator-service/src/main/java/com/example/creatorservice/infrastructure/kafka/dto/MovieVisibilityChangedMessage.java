package com.example.creatorservice.infrastructure.kafka.dto;

// topic: movie.visibility
// receiver: movie-service (ES visibility 필드만 업데이트)
// 최초 PUBLIC 이후 visibility 변경 시마다 발행
public record MovieVisibilityChangedMessage(
        Long movieId,
        String visibility  // "PUBLIC" | "PRIVATE"
) {}