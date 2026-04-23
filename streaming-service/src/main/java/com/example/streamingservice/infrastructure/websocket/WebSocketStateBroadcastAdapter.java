package com.example.streamingservice.infrastructure.websocket;

import com.example.streamingservice.application.constants.WsDestinations;
import com.example.streamingservice.application.dto.ChatMessage;
import com.example.streamingservice.application.dto.StateMessage;
import com.example.streamingservice.application.dto.ViewerCountMessage;
import com.example.streamingservice.application.port.StateBroadcastPort;
import com.example.streamingservice.domain.StreamState;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class WebSocketStateBroadcastAdapter implements StateBroadcastPort {

	private final SimpMessagingTemplate template;

	@Override
	public void broadcastState(long scheduleId, StreamState state) {
		template.convertAndSend(
			WsDestinations.stateTopic(scheduleId),
			new StateMessage(state, Instant.now())
		);
	}

	@Override
	public void broadcastChat(long scheduleId, ChatMessage message) {
		template.convertAndSend(WsDestinations.chatTopic(scheduleId), message);
	}

	@Override
	public void broadcastViewerCount(long scheduleId, long count) {
		template.convertAndSend(
			WsDestinations.viewersTopic(scheduleId),
			new ViewerCountMessage(count, Instant.now())
		);
	}
}