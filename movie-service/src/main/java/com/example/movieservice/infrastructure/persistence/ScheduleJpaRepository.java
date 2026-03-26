package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {
}
