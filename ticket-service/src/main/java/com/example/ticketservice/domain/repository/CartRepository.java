package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.model.Cart;

import java.util.List;
import java.util.UUID;

public interface CartRepository {

    Cart save(Cart cart);

    void deleteByUserIdAndScheduleId(UUID userId, Long scheduleId);

    boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId);

    List<Cart> findAllByScheduleId(Long scheduleId);

    List<Cart> findAllByUserId(UUID userId);

    void deleteAllByScheduleId(Long scheduleId);
}