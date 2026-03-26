package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;
import java.util.UUID;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findFirstByScheduleAndStatusOrderByTicketNumAsc(Schedule schedule, TicketStatus status);
    Page<Ticket> findAllByUserId(UUID userId, Pageable pageable);
    List<Ticket> findAllBySchedule(Schedule schedule);
    List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag);

    // Bulk Update: 건별 UPDATE 대신 단일 쿼리로 일괄 처리하여 성능 최적화
    @Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus WHERE t.schedule.id IN :scheduleIds AND t.status = :currentStatus")
    int bulkUpdateStatus(@Param("scheduleIds") List<Long> scheduleIds,
                         @Param("currentStatus") TicketStatus currentStatus,
                         @Param("newStatus") TicketStatus newStatus);
}
