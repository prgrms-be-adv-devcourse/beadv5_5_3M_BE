package com.example.ticketservice.infrastructure.event;

import com.example.ticketservice.application.event.CartClosedEvent;
import com.example.ticketservice.application.event.TicketCancelledEvent;
import com.example.ticketservice.application.event.TicketPaidEvent;
import com.example.ticketservice.application.event.TicketRefundedEvent;
import com.example.ticketservice.application.event.TicketReservedEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.port.out.EventPublisherPort;
import com.example.ticketservice.infrastructure.messaging.dto.event.CartClosedMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketCancelledMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketPaidMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketRefundedMessage;
import com.example.ticketservice.infrastructure.messaging.dto.event.TicketReservedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventListener {

    private static final String STOCK_KEY_PREFIX = "stock:schedule:";
    private static final String TICKET_RESERVED_TOPIC = "ticket.reserved";
    private static final String TICKET_CANCELLED_TOPIC = "ticket.cancelled";
    private static final String TICKET_PAID_TOPIC = "ticket.paid";
    private static final String TICKET_REFUNDED_TOPIC = "ticket.refunded";
    private static final String CART_CLOSED_TOPIC = "cart.closed";

    private final EventPublisherPort eventPublisherPort;
    private final CachePort cachePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketReserved(TicketReservedEvent event) {
        eventPublisherPort.publish(TICKET_RESERVED_TOPIC, event.ticketId().toString(),
                new TicketReservedMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
        log.debug("ticket.reserved 발행 - ticketId={}", event.ticketId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketCancelled(TicketCancelledEvent event) {
        eventPublisherPort.publish(TICKET_CANCELLED_TOPIC, event.ticketId().toString(),
                new TicketCancelledMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
        log.debug("ticket.cancelled 발행 - ticketId={}", event.ticketId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketPaid(TicketPaidEvent event) {
        eventPublisherPort.publish(TICKET_PAID_TOPIC, event.ticketId().toString(),
                new TicketPaidMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
        log.debug("ticket.paid 발행 - ticketId={}", event.ticketId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketRefunded(TicketRefundedEvent event) {
        // 티켓팅 진행 중이면 Redis 재고 복구 (stock 키가 없으면 INCR 스킵)
        String stockKey = STOCK_KEY_PREFIX + event.scheduleId();
        if (cachePort.exists(stockKey)) {
            cachePort.increment(stockKey);
            log.debug("stock 복구 - scheduleId={}", event.scheduleId());
        }
        eventPublisherPort.publish(TICKET_REFUNDED_TOPIC, event.ticketId().toString(),
                new TicketRefundedMessage(event.ticketId(), event.scheduleId(), event.userId(), event.cookie()));
        log.debug("ticket.refunded 발행 - ticketId={}", event.ticketId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCartClosed(CartClosedEvent event) {
        eventPublisherPort.publish(CART_CLOSED_TOPIC, event.scheduleId().toString(),
                new CartClosedMessage(event.scheduleId(), event.caseType(), event.seats()));
        log.info("cart.closed 발행 - scheduleId={}, caseType={}", event.scheduleId(), event.caseType());
    }
}