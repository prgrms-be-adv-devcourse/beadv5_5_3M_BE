package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.request.AddCartItemRequest;
import com.example.ticketservice.application.dto.response.CartItemResponse;
import com.example.ticketservice.application.usecase.CartItemUseCase;
import com.example.ticketservice.common.exception.CartItemErrorCode;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.event.CartItemAddedEvent;
import com.example.ticketservice.domain.event.CartItemRemovedEvent;
import com.example.ticketservice.domain.model.CartItem;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.CartItemRepository;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CartItemService implements CartItemUseCase {

    private final CartItemRepository cartItemRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CartItemResponse addCartItem(UUID userId, AddCartItemRequest request) {
        log.info("[CartItem] 담기 요청 - userId={}, scheduleId={}", userId, request.scheduleId());

        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> {
                    log.warn("[CartItem] 스케줄 없음 - scheduleId={}", request.scheduleId());
                    return ScheduleErrorCode.NOT_FOUND.of(request.scheduleId());
                });

        if (cartItemRepository.existsByUserIdAndScheduleId(userId, request.scheduleId())) {
            log.warn("[CartItem] 중복 담기 시도 - userId={}, scheduleId={}", userId, request.scheduleId());
            throw CartItemErrorCode.ALREADY_IN_CART.of(request.scheduleId());
        }

        if (!schedule.getStatus().equals(ScheduleStatus.CART)) {
            log.warn("[CartItem] 장바구니 기간 만료 - userId={}, scheduleId={}", userId, request.scheduleId());
            throw ScheduleErrorCode.CART_PERIOD_EXPIRED.of(request.scheduleId());
        }

        CartItem cartItem = CartItem.create(userId, schedule);
        CartItemResponse response = CartItemResponse.from(cartItemRepository.save(cartItem));
        eventPublisher.publishEvent(new CartItemAddedEvent(request.scheduleId()));
        log.info("[CartItem] 담기 완료 - cartItemId={}, userId={}, scheduleId={}", response.cartItemId(), userId, request.scheduleId());
        return response;
    }

    @Override
    public List<CartItemResponse> getCartItems(UUID userId) {
        log.debug("[CartItem] 목록 조회 - userId={}", userId);
        List<CartItemResponse> items = cartItemRepository.findAllByUserId(userId).stream()
                .map(CartItemResponse::from)
                .toList();
        log.debug("[CartItem] 목록 조회 완료 - userId={}, count={}", userId, items.size());
        return items;
    }

    @Override
    @Transactional
    public void removeCartItem(UUID userId, Long cartItemId) {
        log.info("[CartItem] 삭제 요청 - userId={}, cartItemId={}", userId, cartItemId);

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> {
                    log.warn("[CartItem] 항목 없음 - cartItemId={}", cartItemId);
                    return CartItemErrorCode.NOT_FOUND.of(cartItemId);
                });

        cartItemRepository.delete(cartItem);
        eventPublisher.publishEvent(new CartItemRemovedEvent(cartItem.getSchedule().getId()));
        log.info("[CartItem] 삭제 완료 - cartItemId={}", cartItemId);
    }
}
