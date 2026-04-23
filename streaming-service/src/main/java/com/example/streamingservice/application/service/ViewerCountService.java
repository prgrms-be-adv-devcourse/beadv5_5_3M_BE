package com.example.streamingservice.application.service;

import com.example.streamingservice.application.port.StateBroadcastPort;
import com.example.streamingservice.application.port.ViewerCachePort;
import com.example.streamingservice.application.usecase.ViewerCountUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViewerCountService implements ViewerCountUseCase {

	private final ViewerCachePort viewerCache;
	private final StateBroadcastPort stateBroadcast;

	@Override
	public void onConnect(long scheduleId, UUID userId) {
		viewerCache.add(scheduleId, userId);
		stateBroadcast.broadcastViewerCount(scheduleId, viewerCache.count(scheduleId));
	}

	@Override
	public void onDisconnect(long scheduleId, UUID userId) {
		viewerCache.remove(scheduleId, userId);
		stateBroadcast.broadcastViewerCount(scheduleId, viewerCache.count(scheduleId));
	}

	@Override
	@Scheduled(fixedRateString = "${streaming.viewer.broadcast-interval-seconds}000")
	public void broadcastScheduled() {
		for (Long scheduleId : viewerCache.activeScheduleIds()) {
			stateBroadcast.broadcastViewerCount(scheduleId, viewerCache.count(scheduleId));
		}
	}
}