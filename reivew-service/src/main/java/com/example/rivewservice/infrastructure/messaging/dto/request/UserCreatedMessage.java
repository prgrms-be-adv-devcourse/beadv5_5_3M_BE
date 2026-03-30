package com.example.rivewservice.infrastructure.messaging.dto.request;

import java.util.UUID;

//topic: user.created
public record UserCreatedMessage(
        UUID userId,
        String nickname,
        String profileUrl
) {
}
