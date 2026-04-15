package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.response.CartItemResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService implements CartUseCase {

    public static final String CART_COUNT_KEY_PREFIX = "cart:count:schedule:";

    private final CartRepository cartRepository;
    private final ScheduleRepository scheduleRepository;
    private final CachePort cachePort;

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
        cachePort.increment(CART_COUNT_KEY_PREFIX + scheduleId);
    }

    @Transactional
    @Override
    public void removeFromCart(UUID userId, Long scheduleId) {
        cartRepository.deleteByUserIdAndScheduleId(userId, scheduleId);
        cachePort.decrement(CART_COUNT_KEY_PREFIX + scheduleId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CartItemResponse> getMyCart(UUID userId) {
        return cartRepository.findAllByUserId(userId).stream()
                .map(cart -> {
                    Schedule schedule = scheduleRepository.findById(cart.getScheduleId())
                            .orElseThrow(() -> ScheduleErrorCode.NOT_FOUND.of(cart.getScheduleId()));
                    return CartItemResponse.from(cart, schedule);
                })
                .toList();
    }

    @Override
    public long getCartCount(Long scheduleId) {
        Long count = cachePort.getCounter(CART_COUNT_KEY_PREFIX + scheduleId);
        return count != null ? count : 0L;
    }
}