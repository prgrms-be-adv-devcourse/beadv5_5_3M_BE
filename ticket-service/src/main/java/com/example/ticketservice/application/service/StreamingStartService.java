package com.example.ticketservice.application.service;

import com.example.ticketservice.application.usecase.StreamingStartUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingStartService implements StreamingStartUseCase {

    private final ScheduleRepository scheduleRepository;

    @Transactional
    @Override
    public void execute(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));
        if (schedule.getStatus() != ScheduleStatus.TICKETING) {
            log.warn("스트리밍 시작 스킵 - scheduleId={}, currentStatus={}", scheduleId, schedule.getStatus());
            return;
        }
        schedule.startStreaming();
        scheduleRepository.save(schedule);
        log.info("스트리밍 시작 - scheduleId={}", scheduleId);
    }
}