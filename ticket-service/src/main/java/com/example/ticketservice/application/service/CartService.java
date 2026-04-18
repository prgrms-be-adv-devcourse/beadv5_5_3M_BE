package com.example.ticketservice.application.service;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.dto.response.CartItemResponse;
import com.example.ticketservice.application.event.CartUpdatedEvent;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.usecase.CartUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Cart;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.CartRepository;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService implements CartUseCase {

    private final CartRepository cartRepository;
    private final ScheduleRepository scheduleRepository;
    private final CachePort cachePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void addToCart(UUID userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(scheduleId));

        if (schedule.getStatus() != ScheduleStatus.CART) {
            throw ScheduleErrorCode.NOT_IN_CART_PERIOD.of(scheduleId);
        }

        if (cartRepository.existsByUserIdAndScheduleId(userId, scheduleId)) {
            throw TicketErrorCode.ALREADY_IN_CART.of(scheduleId);
        }

        cartRepository.save(Cart.of(userId, scheduleId));
        // Redis 카운터 조작은 AFTER_COMMIT으로 위임 — DB 롤백 시 Redis 불일치 방지
        eventPublisher.publishEvent(new CartUpdatedEvent(scheduleId, userId, true));
    }

    @Transactional
    @Override
    public void removeFromCart(UUID userId, Long scheduleId) {
        cartRepository.deleteByUserIdAndScheduleId(userId, scheduleId);
        // Redis 카운터 조작은 AFTER_COMMIT으로 위임 — DB 롤백 시 Redis 불일치 방지
        eventPublisher.publishEvent(new CartUpdatedEvent(scheduleId, userId, false));
    }

    @Transactional(readOnly = true)
    @Override
    public List<CartItemResponse> getMyCart(UUID userId) {
        List<Cart> carts = cartRepository.findAllByUserId(userId);
        if (carts.isEmpty()) {
            return List.of();
        }

        List<Long> scheduleIds = carts.stream().map(Cart::getScheduleId).toList();
        Map<Long, Schedule> scheduleMap = scheduleRepository.findAllById(scheduleIds).stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));

        return carts.stream()
                .map(cart -> {
                    Schedule schedule = scheduleMap.get(cart.getScheduleId());
                    if (schedule == null) {
                        throw ScheduleErrorCode.NOT_FOUND.of(cart.getScheduleId());
                    }
                    return CartItemResponse.from(cart, schedule);
                })
                .toList();
    }

    @Override
    public long getCartCount(Long scheduleId) {
        Long count = cachePort.getCounter(RedisKeys.CART_COUNT + scheduleId);
        return count != null ? count : 0L;
    }
}