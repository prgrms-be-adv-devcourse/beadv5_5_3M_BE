package com.example.ticketservice.infrastructure.persistence.impl;

import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.persistence.ScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {
    private final ScheduleJpaRepository scheduleJpaRepository;

    @Override
    public Schedule save(Schedule schedule) {
        return scheduleJpaRepository.save(schedule);
    }

    @Override
    public Optional<Schedule> findById(Long id) {
        return scheduleJpaRepository.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return scheduleJpaRepository.existsById(id);
    }

    @Override
    public List<Schedule> findAllByStartTimeBetween(LocalDateTime from, LocalDateTime to) {
        return scheduleJpaRepository.findAllByStartTimeBetween(from, to);
    }
}
