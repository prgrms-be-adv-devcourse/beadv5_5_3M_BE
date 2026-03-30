package com.example.movieservice.application.usecase;

import com.example.movieservice.infrastructure.kafka.dto.consume.*;

public interface CreatorUseCase {
    void handleCreatorCreated(CreatorCreatedMessage msg);
    void handleCreatorUpdated(CreatorUpdatedMessage msg);
}
