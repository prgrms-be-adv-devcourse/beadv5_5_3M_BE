package com.example.streamingservice.application.service;

import com.example.streamingservice.application.port.KickNotifierPort;
import com.example.streamingservice.application.port.SessionCachePort;
import com.example.streamingservice.application.port.StateBroadcastPort;
import com.example.streamingservice.application.port.ViewerCachePort;
import com.example.streamingservice.application.usecase.LifecycleUseCase;
import com.example.streamingservice.domain.SessionKickReason;
import com.example.streamingservice.domain.StreamState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LifecycleService implements LifecycleUseCase {

	private final StateBroadcastPort stateBroadcast;
	private final SessionCachePort sessionCache;
	private final ViewerCachePort viewerCache;
	private final KickNotifierPort kickNotifier;

	@Override
	public void onLobbyOpen(long scheduleId) {
		stateBroadcast.broadcastState(scheduleId, StreamState.LOBBY_OPEN);
	}

	@Override
	public void onStartingSoon(long scheduleId) {
		stateBroadcast.broadcastState(scheduleId, StreamState.STARTING_SOON);
	}

	@Override
	public void onStarted(long scheduleId) {
		stateBroadcast.broadcastState(scheduleId, StreamState.STARTED);
	}

	@Override
	public void onEndingSoon(long scheduleId) {
		stateBroadcast.broadcastState(scheduleId, StreamState.ENDING_SOON);
	}

	@Override
	public void onEnded(long scheduleId) {
		stateBroadcast.broadcastState(scheduleId, StreamState.ENDED);
	}

	@Override
	public void onForceExit(long scheduleId) {
		stateBroadcast.broadcastState(scheduleId, StreamState.FORCE_EXIT);
		Set<UUID> users = sessionCache.scanUsersByScheduleId(scheduleId);
		for (UUID userId : users) {
			kickNotifier.notify(userId, SessionKickReason.FORCE_EXIT);
			kickNotifier.forceClose(userId);
			sessionCache.evict(userId, null);
		}
		viewerCache.purge(scheduleId);
	}
}