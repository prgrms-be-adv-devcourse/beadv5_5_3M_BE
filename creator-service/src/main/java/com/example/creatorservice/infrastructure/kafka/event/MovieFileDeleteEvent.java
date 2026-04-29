package com.example.creatorservice.infrastructure.kafka.event;

public record MovieFileDeleteEvent(String imageUrl, String videoUrl) {}