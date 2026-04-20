package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.model.Schedule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {
    Schedule save(Schedule schedule);
    Optional<Schedule> findById(Long id);
    List<Schedule> findAllById(Collection<Long> ids);
    boolean existsById(Long id);
}