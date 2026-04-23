package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {
    boolean existsById(Long id);
}