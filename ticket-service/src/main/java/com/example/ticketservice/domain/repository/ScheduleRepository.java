package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.model.Schedule;

import java.util.Optional;

public interface ScheduleRepository {
    Schedule save(Schedule schedule);
    Optional<Schedule> findById(Long id);
    boolean existsById(Long id);
}