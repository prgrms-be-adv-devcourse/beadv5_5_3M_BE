package com.example.streamingservice.application.usecase;

public interface LifecycleUseCase {

	void onLobbyOpen(long scheduleId);

	void onStartingSoon(long scheduleId);

	void onStarted(long scheduleId);

	void onEndingSoon(long scheduleId);

	void onEnded(long scheduleId);

	void onForceExit(long scheduleId);
}