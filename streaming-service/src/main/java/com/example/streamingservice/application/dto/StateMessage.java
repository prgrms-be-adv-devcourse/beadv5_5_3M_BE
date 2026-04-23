package com.example.streamingservice.application.dto;

import java.time.Instant;

import com.example.streamingservice.domain.StreamState;

public record StateMessage(StreamState state, Instant at) {
}