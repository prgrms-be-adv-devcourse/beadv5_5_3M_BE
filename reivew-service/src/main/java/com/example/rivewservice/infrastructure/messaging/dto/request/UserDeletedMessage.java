package com.example.rivewservice.infrastructure.messaging.dto.request;

import java.util.UUID;

//topic: user.deleted
public record UserDeletedMessage(
        UUID userId
) {
}
