package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.response.schedule.ScheduleByDateResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleUseCase {

    List<ScheduleForUserResponse> getSpecificMovieSchedule(Long movieId);

    List<ScheduleByDateResponse> getSchedulesByDate(LocalDate date);

    void decreaseSeat(Long scheduleId);

    void increaseSeat(Long scheduleId);
}