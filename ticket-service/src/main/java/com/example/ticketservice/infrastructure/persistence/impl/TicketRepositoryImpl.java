package com.example.ticketservice.infrastructure.persistence.impl;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.persistence.TicketJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository{
    private final TicketJpaRepository jpaRepository;

    @Override
    public Ticket save(Ticket ticket) {
        return jpaRepository.save(ticket);
    }

    @Override
    public List<Ticket> saveAll(List<Ticket> tickets) {
        return jpaRepository.saveAll(tickets);
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Ticket> findFirstAvailableBySchedule(Schedule schedule) {
        return jpaRepository.findFirstByScheduleAndStatusOrderByTicketNumAsc(schedule, TicketStatus.AVAILABLE);
    }

    @Override
    public Page<Ticket> findAllByUserId(UUID userId, int page, int size) {
        return jpaRepository.findAllByUserId(userId, PageRequest.of(page, size));
    }

    @Override
    public List<Ticket> findAllBySchedule(Schedule schedule) {
        return  jpaRepository.findAllBySchedule(schedule);
    }

    @Override
    public List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag) {
        return jpaRepository.findAllByStatusAndProvideFlag(status, provideFlag);
    }

    @Override
    public List<Ticket> findAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status) {
        return jpaRepository.findAllByScheduleIdAndStatus(scheduleId, status);
    }
}
