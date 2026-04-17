package com.example.ticketservice.application.service;

import com.example.ticketservice.application.usecase.StreamingFinishUseCase;
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
public class StreamingFinishService implements StreamingFinishUseCase {

    private final ScheduleRepository scheduleRepository;

    @Transactional
    @Override
    public void execute(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));
        if (schedule.getStatus() != ScheduleStatus.STREAMING) {
            log.warn("스트리밍 종료 스킵 - scheduleId={}, currentStatus={}", scheduleId, schedule.getStatus());
            return;
        }
        schedule.finishStreaming();
        scheduleRepository.save(schedule);
        log.info("스트리밍 종료 - scheduleId={}", scheduleId);
    }
}