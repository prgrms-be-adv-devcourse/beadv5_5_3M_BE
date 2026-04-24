package com.example.streamingservice.application.dto;

public record ServeHlsQuery(long scheduleId, String file, String token) {
}