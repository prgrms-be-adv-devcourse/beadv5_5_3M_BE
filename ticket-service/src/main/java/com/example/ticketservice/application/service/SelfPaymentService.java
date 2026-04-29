package com.example.ticketservice.application.service;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.response.DeductCookieResponse;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.event.TicketPaidEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.application.port.out.UserPort;
import com.example.ticketservice.application.usecase.SelfPaymentUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
import com.example.ticketservice.infrastructure.messaging.dto.event.QueueDrainMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfPaymentService implements SelfPaymentUseCase {

    private final TicketRepository ticketRepository;
    private final UserPort userPort;
    private final CachePort cachePort;
    private final ApplicationEventPublisher eventPublisher;
    private final EventPublisherPort eventPublisherPort;

    @Transactional
    @Override
    public TicketResponse pay(UUID userId, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> TicketErrorCode.NOT_FOUND.of(ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw TicketErrorCode.NOT_YOUR_TICKET.of(ticketId);
        }

        if (ticket.getStatus() != TicketStatus.RESERVED) {
            throw TicketErrorCode.NOT_RESERVED.of(ticketId);
        }

        Schedule schedule = ticket.getSchedule();

        // 영상 시작 10분 전부터(LOBBY 진입 후) 결제 차단
        if (schedule.getStatus() != ScheduleStatus.TICKETING) {
            throw ScheduleErrorCode.NOT_IN_TICKETING.of(schedule.getId());
        }

        // 쿠키 차감 먼저 — 실패 시 상태 전환 없이 예외 발생
        DeductCookieResponse response = userPort.deductTicketFee(
                new DeductCookieRequest(ticketId, schedule.getCookie(), userId));

        if (!response.flag()) {
            // RESERVED 상태이므로 stock 복구 → queue.drain 발행 (대기열 자동 처리 트리거)
            // rollback 경로이므로 AFTER_COMMIT 이벤트 사용 불가 → Kafka 직접 발행
            String stockKey = RedisKeys.STOCK + schedule.getId();
            if (cachePort.exists(stockKey)) {
                cachePort.increment(stockKey);
                eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, schedule.getId().toString(),
                        new QueueDrainMessage(schedule.getId()));
            }
            throw TicketErrorCode.INSUFFICIENT_BALANCE.of((long) schedule.getCookie());
        }

        // DB 커밋 실패 시 쿠키 차감 보상 — HTTP 성공 후 DB 롤백되면 쿠키만 차감되는 불일치 방지
        CookieCompensationHelper.registerRollbackRefund(userPort, ticketId, schedule.getCookie(), userId);

        // 결제 성공 후 상태 전환 RESERVED → CONFIRMED
        ticket.pay();
        ticketRepository.save(ticket);

        // DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서 Kafka ticket.paid 발행
        eventPublisher.publishEvent(new TicketPaidEvent(ticketId, schedule.getId(), userId, schedule.getCookie()));
        log.info("자율결제 완료 - ticketId={}, userId={}", ticketId, userId);

        return TicketResponse.from(ticket);
    }
}