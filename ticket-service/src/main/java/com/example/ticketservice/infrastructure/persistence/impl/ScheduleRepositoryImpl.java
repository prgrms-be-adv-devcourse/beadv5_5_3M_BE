package com.example.ticketservice.infrastructure.persistence.impl;

import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.persistence.ScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
    public List<Schedule> findAllById(Collection<Long> ids) {
        return scheduleJpaRepository.findAllById(ids);
    }

    @Override
    public boolean existsById(Long id) {
        return scheduleJpaRepository.existsById(id);
    }

    @Override
    public Page<Schedule> findAllByStatusIn(Collection<ScheduleStatus> statuses, Pageable pageable) {
        return scheduleJpaRepository.findAllByStatusIn(statuses, pageable);
    }

    @Override
    public List<Schedule> findAllByMovieIdAndStatusInOrderByStartTimeAsc(Long movieId, Collection<ScheduleStatus> statuses) {
        return scheduleJpaRepository.findAllByMovieIdAndStatusInOrderByStartTimeAsc(movieId, statuses);
    }
}