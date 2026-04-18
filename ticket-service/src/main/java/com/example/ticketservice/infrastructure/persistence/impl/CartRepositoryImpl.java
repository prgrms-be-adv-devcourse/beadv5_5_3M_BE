package com.example.ticketservice.infrastructure.persistence.impl;

import com.example.ticketservice.domain.model.Cart;
import com.example.ticketservice.domain.repository.CartRepository;
import com.example.ticketservice.infrastructure.persistence.CartJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    private final CartJpaRepository cartJpaRepository;

    @Override
    public Cart save(Cart cart) {
        return cartJpaRepository.save(cart);
    }

    @Override
    public void deleteByUserIdAndScheduleId(UUID userId, Long scheduleId) {
        cartJpaRepository.deleteByUserIdAndScheduleId(userId, scheduleId);
    }

    @Override
    public boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId) {
        return cartJpaRepository.existsByUserIdAndScheduleId(userId, scheduleId);
    }

    @Override
    public List<Cart> findAllByScheduleId(Long scheduleId) {
        return cartJpaRepository.findAllByScheduleId(scheduleId);
    }

    @Override
    public List<Cart> findAllByUserId(UUID userId) {
        return cartJpaRepository.findAllByUserId(userId);
    }

    @Override
    public void deleteAllByScheduleId(Long scheduleId) {
        cartJpaRepository.deleteAllByScheduleId(scheduleId);
    }
}