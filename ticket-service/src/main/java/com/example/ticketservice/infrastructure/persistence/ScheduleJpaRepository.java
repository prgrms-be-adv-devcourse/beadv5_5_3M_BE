package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {
    boolean existsById(Long id);
    List<Schedule> findAllByStartTimeBetween(LocalDateTime from, LocalDateTime to);
}
