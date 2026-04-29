package com.example.ticketservice.application.service;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.application.usecase.TicketingCloseUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
import com.example.ticketservice.infrastructure.messaging.dto.event.QueueTerminatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketingCloseService implements TicketingCloseUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CachePort cachePort;
    private final EventPublisherPort eventPublisherPort;

    @Transactional
    @Override
    public void execute(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        // TICKETING → LOBBY (이중 트리거 보호)
        schedule.closeTicketing();
        scheduleRepository.save(schedule);

        // Redis 티켓팅 키 명시적 정리. TTL이 같은 시점에 만료되도록 설정돼 있으나
        // 경계 시점의 자율결제/큐 INCR 경합을 막으려면 명시적 DEL이 더 안전.
        cachePort.delete(RedisKeys.STOCK + scheduleId);
        cachePort.delete(RedisKeys.QUEUE + scheduleId);
        cachePort.delete(RedisKeys.PAYING + scheduleId);
        cachePort.delete(RedisKeys.SEATS + scheduleId);
        cachePort.delete(RedisKeys.COOKIE + scheduleId);
        cachePort.delete(RedisKeys.START_TIME + scheduleId);

        // 대기열에 남은 사용자 전원 실패 알림 — notification-service 수신
        eventPublisherPort.publish(KafkaTopics.QUEUE_TERMINATED, scheduleId.toString(),
                new QueueTerminatedMessage(scheduleId));

        log.info("티켓팅 마감 - scheduleId={}", scheduleId);
    }
}