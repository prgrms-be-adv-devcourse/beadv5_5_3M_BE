package com.example.streamingservice.domain;

import java.util.Optional;

public interface ScheduleRepository {

	Optional<Schedule> findById(long scheduleId);

	Schedule save(Schedule schedule);
}