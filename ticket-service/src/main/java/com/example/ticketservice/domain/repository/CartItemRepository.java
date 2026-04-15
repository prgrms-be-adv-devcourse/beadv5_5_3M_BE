package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.model.CartItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    void delete(CartItem cartItem);

    Optional<CartItem> findById(Long cartItemId);

    boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId);

    List<CartItem> findAllByUserId(UUID userId);

    long countByScheduleId(Long scheduleId);
}
