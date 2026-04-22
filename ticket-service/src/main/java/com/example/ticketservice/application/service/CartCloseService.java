package com.example.ticketservice.application.service;

import com.example.ticketservice.application.event.CartClosedEvent;
import com.example.ticketservice.application.usecase.CartCloseUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.model.Cart;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.CartRepository;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import com.example.ticketservice.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartCloseService implements CartCloseUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CartRepository cartRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void execute(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        List<Cart> carts = cartRepository.findAllByScheduleId(scheduleId);
        int demand = carts.size();
        int seats = schedule.getSeats();

        List<UUID> userIds = carts.stream().map(Cart::getUserId).toList();

        String caseType;
        if (demand < seats) {
            // Case A: 수요 < 재고 → 장바구니 유저 전원 가예약
            caseType = "CASE_A";
            List<Ticket> tickets = new ArrayList<>();
            for (int i = 0; i < carts.size(); i++) {
                tickets.add(Ticket.createReserved(schedule, i + 1, carts.get(i).getUserId()));
            }
            ticketRepository.saveAll(tickets);
            log.info("Case A 가예약 완료 - scheduleId={}, reservedCount={}", scheduleId, tickets.size());
        } else {
            // Case B: 수요 >= 재고 → 선착순 대기열 모드
            caseType = "CASE_B";
            log.info("Case B 선착순 모드 - scheduleId={}, demand={}, seats={}", scheduleId, demand, seats);
        }

        schedule.closeCart(); // CART → IN_PROGRESSING
        scheduleRepository.save(schedule);

        cartRepository.deleteAllByScheduleId(scheduleId);

        eventPublisher.publishEvent(new CartClosedEvent(scheduleId, caseType, schedule.getSeats(), userIds));
    }
}