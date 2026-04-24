package com.example.streamingservice.infrastructure.websocket;

import com.example.streamingservice.application.constants.WsDestinations;
import com.example.streamingservice.application.dto.ChatSendRequest;
import com.example.streamingservice.application.dto.SendChatCommand;
import com.example.streamingservice.application.exception.ChatException;
import com.example.streamingservice.application.usecase.ChatUseCase;
import com.example.streamingservice.presentation.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

	private final ChatUseCase chat;
	private final SimpMessagingTemplate template;

	@MessageMapping("/chat/schedule/{scheduleId}")
	public void send(@DestinationVariable long scheduleId,
	                 ChatSendRequest request,
	                 SimpMessageHeaderAccessor acc) {
		Map<String, Object> attrs = acc.getSessionAttributes();
		if (attrs == null) {
			return;
		}
		UUID userId = (UUID) attrs.get(StompAuthChannelInterceptor.ATTR_USER_ID);
		String nickname = (String) attrs.get(StompAuthChannelInterceptor.ATTR_NICKNAME);
		chat.send(new SendChatCommand(userId, nickname, scheduleId, request.content()));
	}

	@MessageExceptionHandler(ChatException.class)
	public void handleChatException(ChatException e, SimpMessageHeaderAccessor acc) {
		Map<String, Object> attrs = acc.getSessionAttributes();
		if (attrs == null) {
			return;
		}
		UUID userId = (UUID) attrs.get(StompAuthChannelInterceptor.ATTR_USER_ID);
		if (userId == null) {
			return;
		}
		template.convertAndSendToUser(
			userId.toString(),
			WsDestinations.QUEUE_ERRORS,
			ErrorResponse.of(e.errorCode())
		);
	}
}