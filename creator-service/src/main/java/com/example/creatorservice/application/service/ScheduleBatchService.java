package com.example.creatorservice.application.service;

import com.example.creatorservice.domain.model.Schedule;
import com.example.creatorservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleBatchService {

    private final ScheduleRepository scheduleRepository;

    /**
     * 매 1분마다 실행: 상영 시작 10분 전인 SCHEDULED 스케줄을 WAITING으로 전환
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void transitionToWaiting() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tenMinutesLater = now.plusMinutes(10);

        List<Schedule> schedules = scheduleRepository.findScheduledToWaiting(now, tenMinutesLater);
        for (Schedule schedule : schedules) {
            schedule.waiting();
            log.info("[Batch] SCHEDULED → WAITING - scheduleId: {}", schedule.getScheduleId());
        }
    }

    /**
     * 매 1분마다 실행: startTime이 된 SCHEDULED/WAITING 스케줄을 ON_AIR로 전환
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void transitionToOnAir() {
        LocalDateTime now = LocalDateTime.now();

        List<Schedule> schedules = scheduleRepository.findToOnAir(now);
        for (Schedule schedule : schedules) {
            schedule.start();
            log.info("[Batch] → ON_AIR - scheduleId: {}", schedule.getScheduleId());
        }
    }

    /**
     * 매 1분마다 실행: endTime이 지난 ON_AIR 스케줄을 COMPLETED로 전환
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void transitionToCompleted() {
        LocalDateTime now = LocalDateTime.now();

        List<Schedule> schedules = scheduleRepository.findOnAirToCompleted(now.minusMinutes(10));
        for (Schedule schedule : schedules) {
            schedule.complete();
            log.info("[Batch] ON_AIR → COMPLETED - scheduleId: {}", schedule.getScheduleId());
        }
    }
}