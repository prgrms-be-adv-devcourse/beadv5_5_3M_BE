package com.example.ticketservice.infrastructure.persistence.impl;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import com.example.ticketservice.domain.repository.TicketRepository;
import com.example.ticketservice.infrastructure.persistence.TicketJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
    public Page<Ticket> findAllByUserId(UUID userId, int page, int size) {
        return jpaRepository.findAllByUserId(userId, PageRequest.of(page, size));
    }

    @Override
    public List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag) {
        return jpaRepository.findAllByStatusAndProvideFlag(status, provideFlag);
    }

    @Override
    public List<Ticket> findAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status) {
        return jpaRepository.findAllByScheduleIdAndStatus(scheduleId, status);
    }

    @Override
    public Slice<Ticket> findByScheduleIdAndStatus(Long scheduleId, TicketStatus status, int page, int size) {
        return jpaRepository.findByScheduleIdAndStatus(scheduleId, status, PageRequest.of(page, size));
    }

    @Override
    public int deleteAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status) {
        return jpaRepository.deleteAllByScheduleIdAndStatus(scheduleId, status);
    }

    @Override
    public long countByScheduleIdAndStatus(Long scheduleId, TicketStatus status) {
        return jpaRepository.countByScheduleIdAndStatus(scheduleId, status);
    }

    @Override
    public void delete(Ticket ticket) {
        jpaRepository.delete(ticket);
    }
}
