package com.example.streamingservice.application.usecase;

import java.util.UUID;

public interface ViewerCountUseCase {

	void onConnect(long scheduleId, UUID userId);

	void onDisconnect(long scheduleId, UUID userId);

	void broadcastScheduled();
}