package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.MovieUpdatedMessage;

public record MovieUpdatedEvent(MovieUpdatedMessage message) {}