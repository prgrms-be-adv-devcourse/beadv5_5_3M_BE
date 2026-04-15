package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    boolean existsByUserIdAndScheduleId(UUID userId, Long scheduleId);

    List<CartItem> findAllByUserId(UUID userId);

    long countByScheduleId(Long scheduleId);
}
