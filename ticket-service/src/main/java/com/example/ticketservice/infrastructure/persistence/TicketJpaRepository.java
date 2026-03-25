package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findAllByUserId(UUID userId, Pageable pageable);
    List<Ticket> findAllBySchedule(Schedule schedule);
    List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag);
}
