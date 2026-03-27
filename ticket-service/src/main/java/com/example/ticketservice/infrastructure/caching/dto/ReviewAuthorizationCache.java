package com.example.ticketservice.infrastructure.caching.dto;

import com.example.ticketservice.domain.model.Ticket;

import java.util.UUID;

public record ReviewAuthorizationCache(
        Long ticketId,
        Long movieId,
        Long scheduleId,
        UUID userId
) {
    public static ReviewAuthorizationCache from(Long ticketId, Long movieId, Long scheduleId, UUID userId) {
        return  new ReviewAuthorizationCache(
          ticketId,
          movieId,
          scheduleId,
          userId
        );
    }
}
