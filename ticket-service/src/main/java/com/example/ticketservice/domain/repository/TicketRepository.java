package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface TicketRepository {
    Ticket save(Ticket ticket);
    Optional<Ticket> findById(Long id);
    Page<Ticket> findAllByUserId(UUID userId, int page, int size);
    List<Ticket> findAllBySchedule(Schedule schedule);
    List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag);
}
