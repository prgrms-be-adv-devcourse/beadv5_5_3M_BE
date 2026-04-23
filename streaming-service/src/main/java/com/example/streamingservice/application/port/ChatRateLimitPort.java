package com.example.streamingservice.application.port;

import java.util.UUID;

public interface ChatRateLimitPort {

	boolean tryAcquire(UUID userId);
}