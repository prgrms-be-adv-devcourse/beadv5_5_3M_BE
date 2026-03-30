package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.schedule.RegisterScheduleRequest;
import com.example.movieservice.presentation.dto.request.schedule.UpdateConfirmRequest;
import com.example.movieservice.presentation.dto.response.schedule.DraftScheduleResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForCreatorResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleUseCase {
    void register(UUID creatorId, RegisterScheduleRequest request);

    List<DraftScheduleResponse> getDraftSchedule(UUID creatorId, LocalDate date);

    void confirmSchedule(UUID creatorId, List<UpdateConfirmRequest> requests);

    void delete(UUID creatorId, Long scheduleId);

    List<ScheduleForUserResponse> getSpecificMovieSchedule(Long movieId);

    List<ScheduleForCreatorResponse> getByCreatorAndDate(UUID creatorId, LocalDate date);

    void decreaseSeat(Long scheduleId);

    void increaseSeat(Long scheduleId);
}
