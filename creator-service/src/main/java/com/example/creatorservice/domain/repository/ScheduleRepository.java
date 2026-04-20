package com.example.creatorservice.domain.repository;

import com.example.creatorservice.domain.model.Schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {
    Schedule save(Schedule schedule);
    Optional<Schedule> findById(Long scheduleId);
    void delete(Schedule schedule);
    boolean existsOverlapping(UUID creatorId, LocalDateTime startTime, LocalDateTime endTime);
    List<Schedule> findAllByCreatorIdAndDate(UUID creatorId, LocalDate date);
    List<Schedule> findScheduledToWaiting(LocalDateTime now, LocalDateTime tenMinutesLater);
    List<Schedule> findToOnAir(LocalDateTime now);
    List<Schedule> findOnAirToCompleted(LocalDateTime tenMinutesAgo);
}