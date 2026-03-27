package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.RegisterScheduleRequest;
import com.example.movieservice.presentation.dto.request.UpdateConfirmRequest;
import com.example.movieservice.presentation.dto.response.DraftScheduleResponse;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleUseCase {
    void register(UUID creatorId, RegisterScheduleRequest request);

    List<DraftScheduleResponse> getDraftSchedule(UUID creatorId, LocalDate date);

    void confirmSchedule(UUID creatorId, List<UpdateConfirmRequest> requests);
}
