package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.ScheduleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleUseCase scheduleUseCase;


}
