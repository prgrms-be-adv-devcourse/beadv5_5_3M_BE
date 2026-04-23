package com.example.streamingservice.infrastructure.websocket;

import com.example.streamingservice.application.dto.SessionMeta;
import com.example.streamingservice.application.exception.ScheduleException;
import com.example.streamingservice.application.exception.SessionException;
import com.example.streamingservice.application.exception.StreamTokenException;
import com.example.streamingservice.application.port.SessionCachePort;
import com.example.streamingservice.application.port.StreamTokenPort;
import com.example.streamingservice.domain.Entitlement;
import com.example.streamingservice.domain.EntitlementRepository;
import com.example.streamingservice.domain.Schedule;
import com.example.streamingservice.domain.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	static final String ATTR_USER_ID = "userId";
	static final String ATTR_SCHEDULE_ID = "scheduleId";
	static final String ATTR_SESSION_ID = "sessionId";
	static final String ATTR_ENTITLEMENT_VERIFIED = "entitlementVerified";
	static final String ATTR_TICKET_ID = "ticketId";
	static final String ATTR_NICKNAME = "nickname";

	private static final Pattern SCHEDULE_DEST =
		Pattern.compile("^/(?:topic|app)/[a-z]+/schedule/(\\d+)(?:/|$)");

	private final StreamTokenPort streamToken;
	private final SessionCachePort sessionCache;
	private final ScheduleRepository scheduleRepository;
	private final EntitlementRepository entitlementRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
		StompCommand command = acc.getCommand();
		if (command == null) {
			return message;
		}
		return switch (command) {
			case CONNECT -> handleConnect(message, acc);
			case SUBSCRIBE, SEND -> handleSubscribeOrSend(message, acc);
			default -> message;
		};
	}

	private Message<?> handleConnect(Message<?> message, StompHeaderAccessor acc) {
		String token = firstHeader(acc, "token");
		if (token == null || token.isBlank()) {
			throw StreamTokenException.invalid();
		}
		UUID sessionId = streamToken.parse(token);
		SessionMeta meta = sessionCache.findBySession(sessionId)
			.orElseThrow(SessionException::sessionExpired);
		Schedule schedule = scheduleRepository.findById(meta.scheduleId())
			.orElseThrow(ScheduleException::notFound);
		if (!schedule.canEnterSession(Instant.now())) {
			throw SessionException.windowClosed();
		}
		Entitlement entitlement = entitlementRepository.find(meta.userId(), meta.scheduleId())
			.orElseThrow(SessionException::noEntitlement);

		Map<String, Object> attrs = acc.getSessionAttributes();
		if (attrs == null) {
			throw SessionException.sessionExpired();
		}
		attrs.put(ATTR_USER_ID, meta.userId());
		attrs.put(ATTR_SCHEDULE_ID, meta.scheduleId());
		attrs.put(ATTR_SESSION_ID, sessionId);
		attrs.put(ATTR_ENTITLEMENT_VERIFIED, Boolean.TRUE);
		attrs.put(ATTR_TICKET_ID, entitlement.getTicketId());
		attrs.put(ATTR_NICKNAME, "관람객#" + entitlement.getTicketId());

		acc.setUser(new StreamingPrincipal(meta.userId().toString()));
		return MessageBuilder.createMessage(message.getPayload(), acc.getMessageHeaders());
	}

	private Message<?> handleSubscribeOrSend(Message<?> message, StompHeaderAccessor acc) {
		Map<String, Object> attrs = acc.getSessionAttributes();
		if (attrs == null || attrs.get(ATTR_ENTITLEMENT_VERIFIED) != Boolean.TRUE) {
			throw SessionException.noEntitlement();
		}
		String destination = acc.getDestination();
		if (destination != null) {
			Matcher m = SCHEDULE_DEST.matcher(destination);
			if (m.find()) {
				long destScheduleId = Long.parseLong(m.group(1));
				Object cachedScheduleId = attrs.get(ATTR_SCHEDULE_ID);
				if (!(cachedScheduleId instanceof Long cached) || cached != destScheduleId) {
					throw SessionException.sessionMismatch();
				}
			}
		}
		return message;
	}

	private static String firstHeader(StompHeaderAccessor acc, String name) {
		List<String> values = acc.getNativeHeader(name);
		return (values == null || values.isEmpty()) ? null : values.get(0);
	}
}
