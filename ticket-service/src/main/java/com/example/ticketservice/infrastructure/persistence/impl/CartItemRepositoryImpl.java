package com.example.ticketservice.infrastructure.persistence.impl;

import com.example.ticketservice.domain.model.CartItem;
import com.example.ticketservice.domain.repository.CartItemRepository;
import com.example.ticketservice.infrastructure.persistence.CartItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartItemRepository {

    private final CartItemJpaRepository cartItemJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public void delete(CartItem cartItem) {
        cartItemJpaRepository.delete(cartItem);
    }

    @Override
    public Optional<CartItem> findById(Long cartItemId) {
        return cartItemJpaRepository.findById(cartItemId);
    }

    @Override
    public boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId) {
        return cartItemJpaRepository.existsByUserIdAndScheduleId(userId, scheduleId);
    }

    @Override
    public List<CartItem> findAllByUserId(UUID userId) {
        return cartItemJpaRepository.findAllByUserId(userId);
    }

    @Override
    public long countByScheduleId(Long scheduleId) {
        return cartItemJpaRepository.countByScheduleId(scheduleId);
    }
}
