package com.example.creatorservice.infrastructure.kafka.dto;

// topic: movie.ai.created
// producer: creator-service (영화 등록 시)
// consumer: ai-service
public record MovieAiCreatedMessage(
        Long movieId,
        String[] category,
        String description
) {}