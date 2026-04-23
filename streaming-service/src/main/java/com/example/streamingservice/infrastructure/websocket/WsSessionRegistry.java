package com.example.streamingservice.infrastructure.websocket;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionRegistry {

	public record SessionInfo(UUID userId, String wsSessionId, long scheduleId) {
	}

	private final ConcurrentHashMap<UUID, SessionInfo> byUser = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, SessionInfo> byWsSessionId = new ConcurrentHashMap<>();

	public void register(UUID userId, String wsSessionId, long scheduleId) {
		SessionInfo info = new SessionInfo(userId, wsSessionId, scheduleId);
		byUser.put(userId, info);
		byWsSessionId.put(wsSessionId, info);
	}

	public Optional<SessionInfo> unregister(String wsSessionId) {
		SessionInfo info = byWsSessionId.remove(wsSessionId);
		if (info != null) {
			byUser.remove(info.userId(), info);
		}
		return Optional.ofNullable(info);
	}

	public Optional<SessionInfo> findByWsSessionId(String wsSessionId) {
		return Optional.ofNullable(byWsSessionId.get(wsSessionId));
	}

	public Optional<SessionInfo> findByUser(UUID userId) {
		return Optional.ofNullable(byUser.get(userId));
	}

	public Optional<String> findWsSessionIdByUser(UUID userId) {
		return findByUser(userId).map(SessionInfo::wsSessionId);
	}
}