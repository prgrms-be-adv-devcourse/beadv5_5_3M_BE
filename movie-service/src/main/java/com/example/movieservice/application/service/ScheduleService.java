package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import com.example.movieservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService implements ScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
}
