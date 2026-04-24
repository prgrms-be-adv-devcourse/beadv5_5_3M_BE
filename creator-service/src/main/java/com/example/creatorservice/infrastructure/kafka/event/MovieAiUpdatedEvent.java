package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.MovieAiUpdatedMessage;

public record MovieAiUpdatedEvent(MovieAiUpdatedMessage message) {}