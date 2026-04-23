package com.example.streamingservice.application.port;

import java.time.Instant;
import java.util.UUID;

public interface StreamTokenPort {

	String issue(UUID sessionId, Instant expiresAt);

	UUID parse(String token);
}