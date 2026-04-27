package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {
    boolean existsById(Long id);
    Page<Schedule> findAllByStatusIn(Collection<ScheduleStatus> statuses, Pageable pageable);
    List<Schedule> findAllByMovieIdAndStatusInOrderByStartTimeAsc(Long movieId, Collection<ScheduleStatus> statuses);
}