package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.MovieAiCreatedMessage;

public record MovieAiCreatedEvent(MovieAiCreatedMessage message) {}