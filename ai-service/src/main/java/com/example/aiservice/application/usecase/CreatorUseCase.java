package com.example.aiservice.application.usecase;

import com.example.aiservice.infrastructure.kafka.dto.consume.CreatorCreatedMessage;
import com.example.aiservice.infrastructure.kafka.dto.consume.CreatorUpdatedMessage;

public interface CreatorUseCase {

    void handleCreatorCreated(CreatorCreatedMessage message);

    void handleCreatorUpdated(CreatorUpdatedMessage message);
}
