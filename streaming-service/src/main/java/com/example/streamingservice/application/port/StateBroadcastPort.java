package com.example.streamingservice.application.port;

import com.example.streamingservice.application.dto.ChatMessage;
import com.example.streamingservice.domain.StreamState;

public interface StateBroadcastPort {

	void broadcastState(long scheduleId, StreamState state);

	void broadcastChat(long scheduleId, ChatMessage message);

	void broadcastViewerCount(long scheduleId, long count);
}