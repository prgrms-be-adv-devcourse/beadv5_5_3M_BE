package com.example.movieservice.application.event;

import java.util.UUID;

public record MovieLikedEvent(
        UUID userId,
        Long movieId,
        String action  // "LIKED" | "UNLIKED"
) {}