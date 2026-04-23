package com.example.userservice.infrastructure.kafka.event;

import com.example.userservice.domain.model.User;

import java.util.UUID;

public record UserUpdatedEvent(
        UUID userId,
        String nickname,
        String profileUrl
) {
    public static UserUpdatedEvent from(User user) {
        return new UserUpdatedEvent(user.getUserId(), user.getNickname(), user.getProfileUrl());
    }
}
