package com.example.streamingservice.application.port;

import java.util.Set;
import java.util.UUID;

public interface ViewerCachePort {

	long add(long scheduleId, UUID userId);

	long remove(long scheduleId, UUID userId);

	long count(long scheduleId);

	Set<UUID> snapshot(long scheduleId);

	void purge(long scheduleId);

	Set<Long> activeScheduleIds();
}