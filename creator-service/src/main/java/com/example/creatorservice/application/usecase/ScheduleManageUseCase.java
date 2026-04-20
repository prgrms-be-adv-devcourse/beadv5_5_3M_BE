package com.example.creatorservice.application.usecase;

import com.example.creatorservice.presentation.dto.req.ConfirmScheduleRequest;
import com.example.creatorservice.presentation.dto.req.RegisterScheduleRequest;
import com.example.creatorservice.presentation.dto.res.ScheduleResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleManageUseCase {

    List<Long> register(UUID creatorId, RegisterScheduleRequest request);

    void confirm(UUID creatorId, ConfirmScheduleRequest request);

    void delete(UUID creatorId, Long scheduleId);

    List<ScheduleResponse> getDraftByDate(UUID creatorId, LocalDate date);
}