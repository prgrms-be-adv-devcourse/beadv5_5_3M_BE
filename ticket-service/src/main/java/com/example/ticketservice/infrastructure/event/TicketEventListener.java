package com.example.ticketservice.infrastructure.event;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.dto.request.RefundCookieRequest;
import com.example.ticketservice.application.event.CartUpdatedEvent;
import com.example.ticketservice.application.event.CartClosedEvent;
import com.example.ticketservice.application.event.TicketingStartedEvent;
import com.example.ticketservice.application.event.TicketPaidEvent;
import com.example.ticketservice.application.event.TicketRefundedEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.application.port.out.UserPort;
import com.example.ticketservice.infrastructure.messaging.dto.event.CartClosedMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.QueueDrainMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketingStartedMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketPaidMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketRefundedMessage;
import com.example.ticketservice.infrastructure.messaging.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class TicketEventListener {

    private final EventPublisherPort eventPublisherPort;
    private final CachePort cachePort;
    private final UserPort userPort;
    private final Duration ticketingCloseLead;

    public TicketEventListener(
            EventPublisherPort eventPublisherPort,
            CachePort cachePort,
            UserPort userPort,
            @Value("${ticket.lifecycle.ticketing-close-lead:PT10M}") Duration ticketingCloseLead) {
        this.eventPublisherPort = eventPublisherPort;
        this.cachePort = cachePort;
        this.userPort = userPort;
        this.ticketingCloseLead = ticketingCloseLead;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketPaid(TicketPaidEvent event) {
        eventPublisherPort.publish(KafkaTopics.TICKET_PAID, event.ticketId().toString(),
                new TicketPaidMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
        eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, event.scheduleId().toString(),
                new QueueDrainMessage(event.scheduleId()));
        log.debug("ticket.paid 발행 - ticketId={}", event.ticketId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketRefunded(TicketRefundedEvent event) {
        // 1. 쿠키 환불 — DB 커밋 후 실행하여 이중 환불 방지
        //    실패해도 stock 복구/Kafka 발행은 반드시 진행해야 대기자 처리가 지연되지 않음
        try {
            userPort.refundCookie(new RefundCookieRequest(event.ticketId(), event.cookie(), event.userId()));
            log.info("쿠키 환불 완료 - ticketId={}, cookie={}", event.ticketId(), event.cookie());
        } catch (Exception e) {
            log.error("쿠키 환불 실패 - ticketId={}, userId={}, 수동 처리 필요", event.ticketId(), event.userId(), e);
        }

        // 2. 티켓팅 진행 중이면 Redis 재고 복구 (stock 키가 없으면 INCR 스킵)
        try {
            String stockKey = RedisKeys.STOCK + event.scheduleId();
            if (cachePort.exists(stockKey)) {
                cachePort.increment(stockKey);
                log.debug("stock 복구 - scheduleId={}", event.scheduleId());
                eventPublisherPort.publish(KafkaTopics.QUEUE_DRAIN, event.scheduleId().toString(),
                        new QueueDrainMessage(event.scheduleId()));
            }
        } catch (Exception e) {
            log.error("stock 복구 실패 - scheduleId={}", event.scheduleId(), e);
        }

        // 3. Kafka ticket.refunded 발행
        try {
            eventPublisherPort.publish(KafkaTopics.TICKET_REFUNDED, event.ticketId().toString(),
                    new TicketRefundedMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
            log.debug("ticket.refunded 발행 - ticketId={}", event.ticketId());
        } catch (Exception e) {
            log.error("ticket.refunded 발행 실패 - ticketId={}", event.ticketId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCartClosed(CartClosedEvent event) {
        eventPublisherPort.publish(KafkaTopics.CART_CLOSED, event.scheduleId().toString(),
                new CartClosedMessage(event.scheduleId(), event.caseType(), event.seats(), event.userIds()));
        log.info("cart.closed 발행 - scheduleId={}, caseType={}", event.scheduleId(), event.caseType());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketingStarted(TicketingStartedEvent event) {
        // Redis 키 설정 — DB 커밋 후 실행하여 DB 롤백 시 Redis 잔류 방지
        Duration ttl = Duration.between(LocalDateTime.now(), event.startTime().minus(ticketingCloseLead));
        if (!ttl.isNegative() && !ttl.isZero()) {
            cachePort.setCounter(RedisKeys.STOCK + event.scheduleId(), event.remaining(), ttl);
            cachePort.setCounter(RedisKeys.PAYING + event.scheduleId(), 0, ttl);
            cachePort.setCounter(RedisKeys.SEATS + event.scheduleId(), event.seats(), ttl);
            cachePort.setCounter(RedisKeys.COOKIE + event.scheduleId(), event.cookie(), ttl);
            cachePort.set(RedisKeys.START_TIME + event.scheduleId(), event.startTime().toString(), ttl);
        }
        eventPublisherPort.publish(KafkaTopics.TICKETING_STARTED, event.scheduleId().toString(),
                new TicketingStartedMessage(event.scheduleId()));
        log.info("ticketing.started 발행 - scheduleId={}, remaining={}", event.scheduleId(), event.remaining());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCartUpdated(CartUpdatedEvent event) {
        // Redis 카운터 조작 — DB 커밋 후 실행하여 DB 롤백 시 Redis 불일치 방지
        String key = RedisKeys.CART_COUNT + event.scheduleId();
        if (event.added()) {
            cachePort.increment(key);
        } else {
            cachePort.decrement(key);
        }
        log.debug("cart count {} - scheduleId={}", event.added() ? "증가" : "감소", event.scheduleId());
    }
}