package com.example.streamingservice.application.dto;

import java.time.Instant;

import com.example.streamingservice.domain.SessionKickReason;

public record KickMessage(SessionKickReason reason, Instant at) {
}