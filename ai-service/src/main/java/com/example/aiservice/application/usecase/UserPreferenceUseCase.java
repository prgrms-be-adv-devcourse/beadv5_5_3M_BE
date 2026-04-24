package com.example.aiservice.application.usecase;

import com.example.aiservice.infrastructure.kafka.dto.consume.UserCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.UserDeletedMessage;

public interface UserPreferenceUseCase {

    void handleUserCreated(UserCreatedMessage message);

    void handleUserDeleted(UserDeletedMessage message);
}
