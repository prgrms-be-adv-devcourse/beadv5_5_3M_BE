package com.example.creatorservice.infrastructure.kafka.event;

import com.example.creatorservice.infrastructure.kafka.dto.MovieUploadedMessage;

public record MovieUploadedEvent(MovieUploadedMessage message) {}