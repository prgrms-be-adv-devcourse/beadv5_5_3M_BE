package com.example.ticketservice.domain.repository;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository {
    Ticket save(Ticket ticket);
    List<Ticket> saveAll(List<Ticket> tickets);
    Optional<Ticket> findById(Long id);
    Page<Ticket> findAllByUserId(UUID userId, int page, int size);
    List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag);
    List<Ticket> findAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status);
    Slice<Ticket> findByScheduleIdAndStatus(Long scheduleId, TicketStatus status, int page, int size);

    int deleteAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status);

    long countByScheduleIdAndStatus(Long scheduleId, TicketStatus status);

    void delete(Ticket ticket);
}