package com.example.streamingservice.infrastructure.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionStore {

	private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

	public void put(String wsSessionId, WebSocketSession session) {
		sessions.put(wsSessionId, session);
	}

	public Optional<WebSocketSession> find(String wsSessionId) {
		return Optional.ofNullable(sessions.get(wsSessionId));
	}

	public void remove(String wsSessionId) {
		sessions.remove(wsSessionId);
	}
}
