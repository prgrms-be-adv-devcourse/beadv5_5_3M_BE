package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;

import java.util.List;

public interface ScheduleUseCase {

    List<ScheduleForUserResponse> getSpecificMovieSchedule(Long movieId);

    void decreaseSeat(Long scheduleId);

    void increaseSeat(Long scheduleId);
}