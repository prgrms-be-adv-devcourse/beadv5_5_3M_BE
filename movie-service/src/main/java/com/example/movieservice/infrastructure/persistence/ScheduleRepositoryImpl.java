package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.Schedule;
import com.example.movieservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {
    private final ScheduleJpaRepository scheduleJpaRepository;

    @Override
    public Optional<Schedule> findById(Long scheduleId) {
        return scheduleJpaRepository.findById(scheduleId);
    }

    @Override
    public Optional<Schedule> findByIdWithLock(Long scheduleId) {
        return scheduleJpaRepository.findByIdWithLock(scheduleId);
    }

    @Override
    public List<Schedule> findUpcomingByMovieId(Long movieId, LocalDateTime now) {
        return scheduleJpaRepository.findUpcomingByMovieId(movieId, now);
    }

    @Override
    public boolean existsConfirmedScheduleByMovieId(Long movieId) {
        return scheduleJpaRepository.existsByMovieMovieIdAndIsConfirmedTrue(movieId);
    }

    @Override
    public List<Movie> findOnAirMovies() {
        return scheduleJpaRepository.findOnAirMovies();
    }

    @Override
    public List<Schedule> findScheduledMovies() {
        return scheduleJpaRepository.findScheduleMovies();
    }

    @Override
    public List<Schedule> findConfirmedByDate(LocalDate date) {
        return scheduleJpaRepository.findConfirmedByDate(date);
    }

    @Override
    public List<Schedule> findConfirmedByDateAndCreator(LocalDate date, UUID creatorId) {
        return scheduleJpaRepository.findConfirmedByDateAndCreator(date, creatorId);
    }
}
