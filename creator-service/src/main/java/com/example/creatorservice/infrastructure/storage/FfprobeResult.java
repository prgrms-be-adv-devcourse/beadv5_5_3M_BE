package com.example.creatorservice.infrastructure.storage;

public record FfprobeResult(
        String codecName,
        Integer durationSeconds
) {
    public boolean isH264() {
        return "h264".equalsIgnoreCase(codecName);
    }
}