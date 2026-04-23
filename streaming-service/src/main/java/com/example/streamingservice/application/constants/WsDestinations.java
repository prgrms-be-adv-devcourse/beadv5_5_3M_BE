package com.example.streamingservice.application.constants;

public final class WsDestinations {

	public static final String TOPIC_CHAT_PREFIX = "/topic/chat/schedule/";
	public static final String TOPIC_VIEWERS_PREFIX = "/topic/viewers/schedule/";
	public static final String TOPIC_STATE_PREFIX = "/topic/state/schedule/";
	public static final String QUEUE_KICK = "/queue/kick";
	public static final String QUEUE_ERRORS = "/queue/errors";
	public static final String APP_CHAT_PREFIX = "/app/chat/schedule/";

	private WsDestinations() {
	}

	public static String chatTopic(long scheduleId) {
		return TOPIC_CHAT_PREFIX + scheduleId;
	}

	public static String viewersTopic(long scheduleId) {
		return TOPIC_VIEWERS_PREFIX + scheduleId;
	}

	public static String stateTopic(long scheduleId) {
		return TOPIC_STATE_PREFIX + scheduleId;
	}
}