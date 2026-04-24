package com.example.userservice.infrastructure.kafka.event;

import java.util.UUID;

public record UserDeletedEvent(
        UUID userId
) {

    public static UserDeletedEvent from(UUID userId) {
        return new UserDeletedEvent(userId);
    }
}
