package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.MovieDeletedMessage;

public record MovieDeletedEvent(MovieDeletedMessage message) {}