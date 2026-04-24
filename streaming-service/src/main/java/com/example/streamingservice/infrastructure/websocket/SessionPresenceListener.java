package com.example.streamingservice.infrastructure.websocket;

import com.example.streamingservice.application.usecase.ViewerCountUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SessionPresenceListener {

	private final ViewerCountUseCase viewerCount;
	private final WsSessionRegistry wsSessionRegistry;

	@EventListener
	public void onConnected(SessionConnectedEvent event) {
		SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.wrap(event.getMessage());
		Map<String, Object> attrs = acc.getSessionAttributes();
		if (attrs == null) {
			return;
		}
		UUID userId = (UUID) attrs.get(StompAuthChannelInterceptor.ATTR_USER_ID);
		Long scheduleId = (Long) attrs.get(StompAuthChannelInterceptor.ATTR_SCHEDULE_ID);
		String wsSessionId = acc.getSessionId();
		if (userId == null || scheduleId == null || wsSessionId == null) {
			return;
		}
		wsSessionRegistry.register(userId, wsSessionId, scheduleId);
		viewerCount.onConnect(scheduleId, userId);
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		String wsSessionId = event.getSessionId();
		wsSessionRegistry.unregister(wsSessionId).ifPresent(info ->
			viewerCount.onDisconnect(info.scheduleId(), info.userId()));
	}
}