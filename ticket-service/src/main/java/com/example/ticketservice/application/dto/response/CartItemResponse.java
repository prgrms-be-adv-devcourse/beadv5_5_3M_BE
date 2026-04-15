package com.example.ticketservice.application.dto.response;

import com.example.ticketservice.domain.model.CartItem;

import java.time.LocalDateTime;

public record CartItemResponse(
        Long cartItemId,
        Long scheduleId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer cookie,
        LocalDateTime createdAt
) {
    public static CartItemResponse from(CartItem cartItem) {
        return new CartItemResponse(
                cartItem.getCartItemId(),
                cartItem.getSchedule().getId(),
                cartItem.getSchedule().getTitle(),
                cartItem.getSchedule().getStartTime(),
                cartItem.getSchedule().getEndTime(),
                cartItem.getSchedule().getCookie(),
                cartItem.getCreatedAt()
        );
    }
}
