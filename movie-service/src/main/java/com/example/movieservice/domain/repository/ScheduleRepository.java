package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Schedule;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {
    boolean existsOverlapping(UUID creatorId, LocalDateTime startTime, LocalDateTime endTime);

    void save(Schedule schedule);

    List<Schedule> findAllByCreatorIdAndDate(UUID creatorId, LocalDate date);

    Optional<Schedule> findById(Long scheduleId);

    void delete(Schedule schedule);

    List<Schedule> findUpcomingByMovieId(Long movieId, LocalDateTime now);

    boolean existsConfirmedScheduleByMovieId(Long movieId);
}
