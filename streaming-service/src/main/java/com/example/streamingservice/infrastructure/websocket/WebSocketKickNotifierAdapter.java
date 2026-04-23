package com.example.streamingservice.infrastructure.websocket;

import com.example.streamingservice.application.constants.WsDestinations;
import com.example.streamingservice.application.dto.KickMessage;
import com.example.streamingservice.application.port.KickNotifierPort;
import com.example.streamingservice.domain.SessionKickReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketKickNotifierAdapter implements KickNotifierPort {

	private static final CloseStatus FORCE_EXIT_CLOSE = new CloseStatus(4002, "FORCE_EXIT");

	private final SimpMessagingTemplate template;
	private final WsSessionRegistry wsSessionRegistry;
	private final WsSessionStore wsSessionStore;

	@Override
	public void notify(UUID userId, SessionKickReason reason) {
		template.convertAndSendToUser(
			userId.toString(),
			WsDestinations.QUEUE_KICK,
			new KickMessage(reason, Instant.now())
		);
	}

	@Override
	public void forceClose(UUID userId) {
		wsSessionRegistry.findWsSessionIdByUser(userId).ifPresent(wsSessionId ->
			wsSessionStore.find(wsSessionId).ifPresent(session -> {
				if (!session.isOpen()) {
					return;
				}
				try {
					session.close(FORCE_EXIT_CLOSE);
				} catch (IOException e) {
					log.warn("force close failed ws={} err={}", wsSessionId, e.toString());
				}
			})
		);
	}
}