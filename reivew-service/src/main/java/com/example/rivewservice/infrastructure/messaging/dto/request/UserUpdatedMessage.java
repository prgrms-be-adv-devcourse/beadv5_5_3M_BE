package com.example.rivewservice.infrastructure.messaging.dto.request;

import java.util.UUID;

//topic: user.updated
public record UserUpdatedMessage(
        UUID userId,
        String nickname,
        String profileUrl
) {
}
