package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.response.CartItemResponse;

import java.util.List;
import java.util.UUID;

public interface CartUseCase {

    void addToCart(UUID userId, Long scheduleId);

    void removeFromCart(UUID userId, Long scheduleId);

    List<CartItemResponse> getMyCart(UUID userId);

    long getCartCount(Long scheduleId);
}