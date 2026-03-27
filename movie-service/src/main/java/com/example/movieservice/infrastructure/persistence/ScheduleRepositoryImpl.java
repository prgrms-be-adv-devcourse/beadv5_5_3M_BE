package com.example.movieservice.infrastructure.persistence;

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
    public boolean existsOverlapping(UUID creatorId, LocalDateTime startTime, LocalDateTime endTime) {
        return scheduleJpaRepository.existsOverlapping(creatorId, startTime, endTime);
    }

    @Override
    public void save(Schedule schedule) {
        scheduleJpaRepository.save(schedule);
    }

    @Override
    public List<Schedule> findAllByCreatorIdAndDate(UUID creatorId, LocalDate date) {
        return scheduleJpaRepository.findAllByCreatorIdAndDate(creatorId, date);
    }

    @Override
    public Optional<Schedule> findById(Long scheduleId) {
        return scheduleJpaRepository.findById(scheduleId);
    }

    @Override
    public void delete(Schedule schedule) {
        scheduleJpaRepository.delete(schedule);
    }

    @Override
    public List<Schedule> findUpcomingByMovieId(Long movieId, LocalDateTime now) {
        return scheduleJpaRepository.findUpcomingByMovieId(movieId, now);
    }
}
