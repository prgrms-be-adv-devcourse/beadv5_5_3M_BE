package com.example.creatorservice.event;

import com.example.creatorservice.domain.model.Creator;

import java.util.UUID;

public record CreatorCreatedEvent(
        UUID creatorId,
        String nickname
) {

    public static CreatorCreatedEvent from(Creator creator) {
        return new CreatorCreatedEvent(creator.getId(), creator.getNickname());
    }
}
