package com.example.streamingservice.application.service;

import com.example.streamingservice.application.dto.ChatMessage;
import com.example.streamingservice.application.dto.SendChatCommand;
import com.example.streamingservice.application.exception.ChatException;
import com.example.streamingservice.application.port.ChatRateLimitPort;
import com.example.streamingservice.application.port.StateBroadcastPort;
import com.example.streamingservice.application.usecase.ChatUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ChatService implements ChatUseCase {

	private final ChatRateLimitPort chatRateLimit;
	private final StateBroadcastPort stateBroadcast;
	private final int maxLength;

	public ChatService(ChatRateLimitPort chatRateLimit,
	                   StateBroadcastPort stateBroadcast,
	                   @Value("${streaming.chat.max-message-length}") int maxLength) {
		this.chatRateLimit = chatRateLimit;
		this.stateBroadcast = stateBroadcast;
		this.maxLength = maxLength;
	}

	@Override
	public void send(SendChatCommand command) {
		if (command.content() == null || command.content().length() > maxLength) {
			throw ChatException.tooLong();
		}
		if (!chatRateLimit.tryAcquire(command.userId())) {
			throw ChatException.rateLimited();
		}
		ChatMessage message = new ChatMessage(
			UUID.randomUUID(),
			command.userId(),
			command.nickname(),
			command.content(),
			Instant.now());
		stateBroadcast.broadcastChat(command.scheduleId(), message);
	}
}