package com.example.userservice.infrastructure.kafka.consumer.dto;

public record MovieDeletedMessage(
        Long movieId
) {}
