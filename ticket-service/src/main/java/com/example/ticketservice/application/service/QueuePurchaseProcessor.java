package com.example.ticketservice.application.service;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.dto.request.DeductCookieRequest;
import com.example.ticketservice.application.dto.response.DeductCookieResponse;
import com.example.ticketservice.application.dto.response.TicketResponse;
import com.example.ticketservice.application.event.TicketPaidEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.UserPort;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueuePurchaseProcessor {

    private final ScheduleRepository scheduleRepository;
    private final TicketRepository ticketRepository;
    private final UserPort userPort;
    private final CachePort cachePort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 단일 유저에 대한 티켓 구매 시도.
     * @return 구매 성공 시 Optional<TicketResponse>, 쿠키 부족 실패 시 Optional.empty()
     *         실패 시 stock 복구 + 트랜잭션 롤백(티켓 save 취소)
     */
    @Transactional
    public Optional<TicketResponse> tryPurchase(Long scheduleId, UUID userId, int ticketNum) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        Ticket ticket = Ticket.createReserved(schedule, ticketNum, userId);
        ticketRepository.save(ticket); // ID 확보를 위해 먼저 저장

        DeductCookieResponse response = userPort.deductTicketFee(
                new DeductCookieRequest(ticket.getId(), schedule.getCookie(), userId));

        if (!response.flag()) {
            cachePort.increment(RedisKeys.STOCK + scheduleId); // 재고 복구 (Redis는 트랜잭션 밖)
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // 티켓 save 롤백
            log.info("구매 실패(쿠키부족) - scheduleId={}, userId={}", scheduleId, userId);
            return Optional.empty();
        }

        // DB 커밋 실패 시 쿠키 차감 보상 — HTTP 성공 후 DB 롤백되면 쿠키만 차감되는 불일치 방지
        CookieCompensationHelper.registerRollbackRefund(userPort, ticket.getId(), schedule.getCookie(), userId);

        ticket.pay(); // RESERVED → CONFIRMED
        // DB 커밋 후 @TransactionalEventListener(AFTER_COMMIT)에서 Kafka ticket.paid 발행
        eventPublisher.publishEvent(new TicketPaidEvent(ticket.getId(), scheduleId, userId, schedule.getCookie()));
        log.info("구매 성공 - scheduleId={}, userId={}, ticketNum={}", scheduleId, userId, ticketNum);
        return Optional.of(TicketResponse.from(ticket));
    }
}