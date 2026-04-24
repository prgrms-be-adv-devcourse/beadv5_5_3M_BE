package com.example.streamingservice.application.dto;

import java.time.Instant;

public record ViewerCountMessage(long count, Instant at) {
}