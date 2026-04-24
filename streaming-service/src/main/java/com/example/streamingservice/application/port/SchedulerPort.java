package com.example.streamingservice.application.port;

import com.example.streamingservice.domain.Schedule;

public interface SchedulerPort {

	void scheduleLifecycle(Schedule schedule);

	void unscheduleLifecycle(long scheduleId);
}