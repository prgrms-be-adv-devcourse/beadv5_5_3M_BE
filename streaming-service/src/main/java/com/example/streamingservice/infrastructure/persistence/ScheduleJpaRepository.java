package com.example.streamingservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.streamingservice.domain.Schedule;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {
}