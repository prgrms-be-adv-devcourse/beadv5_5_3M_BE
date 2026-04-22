package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartJpaRepository extends JpaRepository<Cart, Long> {

    void deleteByUserIdAndScheduleId(UUID userId, Long scheduleId);

    boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId);

    List<Cart> findAllByScheduleId(Long scheduleId);

    List<Cart> findAllByUserId(UUID userId);

    void deleteAllByScheduleId(Long scheduleId);
}