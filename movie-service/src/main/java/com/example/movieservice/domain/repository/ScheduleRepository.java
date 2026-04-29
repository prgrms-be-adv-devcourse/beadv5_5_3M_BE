package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {
    Optional<Schedule> findById(Long scheduleId);

    Optional<Schedule> findByIdWithLock(Long scheduleId);

    List<Schedule> findUpcomingByMovieId(Long movieId, LocalDateTime now);

    boolean existsConfirmedScheduleByMovieId(Long movieId);

    List<Movie> findOnAirMovies();

    List<Schedule> findScheduledMovies();

    List<Schedule> findConfirmedByDate(LocalDate date);
}
