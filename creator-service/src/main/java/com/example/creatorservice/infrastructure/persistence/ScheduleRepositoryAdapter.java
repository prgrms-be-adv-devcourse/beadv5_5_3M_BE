package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.domain.model.Schedule;
import com.example.creatorservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryAdapter implements ScheduleRepository {

    private final ScheduleJpaRepository scheduleJpaRepository;

    @Override
    public Schedule save(Schedule schedule) {
        return scheduleJpaRepository.save(schedule);
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
    public boolean existsOverlapping(UUID creatorId, LocalDateTime startTime, LocalDateTime endTime) {
        return scheduleJpaRepository.existsOverlapping(creatorId, startTime, endTime);
    }

    @Override
    public List<Schedule> findAllByCreatorIdAndDate(UUID creatorId, LocalDate date) {
        return scheduleJpaRepository.findAllByCreatorIdAndDate(creatorId, date);
    }

    @Override
    public List<Schedule> findScheduledToWaiting(LocalDateTime now, LocalDateTime tenMinutesLater) {
        return scheduleJpaRepository.findScheduledToWaiting(now, tenMinutesLater);
    }

    @Override
    public List<Schedule> findToOnAir(LocalDateTime now) {
        return scheduleJpaRepository.findToOnAir(now);
    }

    @Override
    public List<Schedule> findOnAirToCompleted(LocalDateTime tenMinutesAgo) {
        return scheduleJpaRepository.findOnAirToCompleted(tenMinutesAgo);
    }
}