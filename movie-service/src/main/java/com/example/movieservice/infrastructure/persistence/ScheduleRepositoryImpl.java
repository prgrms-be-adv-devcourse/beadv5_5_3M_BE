package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {
    public final ScheduleJpaRepository scheduleJpaRepository;
}
