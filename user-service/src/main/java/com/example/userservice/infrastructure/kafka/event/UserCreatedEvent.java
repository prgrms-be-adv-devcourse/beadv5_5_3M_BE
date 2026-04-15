package com.example.userservice.infrastructure.kafka.event;

import com.example.userservice.domain.model.User;

import java.util.UUID;

public record UserCreatedEvent(
        UUID userId,
        String nickname,
        String profileUrl
) {

    public static UserCreatedEvent from(User user) {
        return new UserCreatedEvent(user.getUserId(), user.getNickname(), user.getProfileUrl());
    }
}
