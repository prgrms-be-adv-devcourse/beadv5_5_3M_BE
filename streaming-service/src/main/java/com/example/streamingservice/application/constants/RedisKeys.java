package com.example.streamingservice.application.constants;

import java.util.UUID;

public final class RedisKeys {

	public static final String SESSION_BY_USER_PREFIX = "stream:session:user:";
	public static final String SESSION_BY_ID_PREFIX = "stream:session:id:";
	public static final String VIEWERS_PREFIX = "stream:viewers:schedule:";
	public static final String CHAT_RATE_LIMIT_PREFIX = "stream:ratelimit:chat:";

	private RedisKeys() {
	}

	public static String sessionByUser(UUID userId) {
		return SESSION_BY_USER_PREFIX + userId;
	}

	public static String sessionById(UUID sessionId) {
		return SESSION_BY_ID_PREFIX + sessionId;
	}

	public static String viewers(long scheduleId) {
		return VIEWERS_PREFIX + scheduleId;
	}

	public static String chatRateLimit(UUID userId) {
		return CHAT_RATE_LIMIT_PREFIX + userId;
	}
}