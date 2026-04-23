package com.example.streamingservice.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.streamingservice.domain.Schedule;
import com.example.streamingservice.domain.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {

	private final ScheduleJpaRepository jpaRepository;

	@Override
	public Optional<Schedule> findById(long scheduleId) {
		return jpaRepository.findById(scheduleId);
	}

	@Override
	public Schedule save(Schedule schedule) {
		return jpaRepository.save(schedule);
	}
}