package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.MovieVisibilityChangedMessage;

public record MovieVisibilityChangedEvent(MovieVisibilityChangedMessage message) {}