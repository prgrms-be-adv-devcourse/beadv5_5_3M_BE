package com.example.streamingservice.application.port;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.streamingservice.application.dto.ActiveSession;
import com.example.streamingservice.application.dto.SessionMeta;

public interface SessionCachePort {

	Optional<ActiveSession> findByUser(UUID userId);

	void put(UUID userId, ActiveSession session, Duration ttl);

	void evict(UUID userId, UUID sessionId);

	Optional<SessionMeta> findBySession(UUID sessionId);

	Set<UUID> scanUsersByScheduleId(long scheduleId);
}