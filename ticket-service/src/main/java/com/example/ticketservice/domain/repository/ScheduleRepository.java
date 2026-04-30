package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {
    Schedule save(Schedule schedule);
    Optional<Schedule> findById(Long id);
    List<Schedule> findAllById(Collection<Long> ids);
    boolean existsById(Long id);
    Page<Schedule> findAllByStatusIn(Collection<ScheduleStatus> statuses, Pageable pageable);
    List<Schedule> findAllByMovieIdAndStatusInOrderByStartTimeAsc(Long movieId, Collection<ScheduleStatus> statuses);
}