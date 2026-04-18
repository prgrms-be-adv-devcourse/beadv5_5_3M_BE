package com.example.ticketservice.infrastructure.persistence;

import com.example.ticketservice.domain.enums.TicketStatus;
import com.example.ticketservice.domain.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findAllByUserId(UUID userId, Pageable pageable);
    List<Ticket> findAllByStatusAndProvideFlag(TicketStatus status, boolean provideFlag);
    Slice<Ticket> findByStatusAndProvideFlag(TicketStatus status, boolean provideFlag, Pageable pageable);
    List<Ticket> findAllByScheduleIdAndStatus(Long scheduleId, TicketStatus status);
    Slice<Ticket> findByScheduleIdAndStatus(Long scheduleId, TicketStatus status, Pageable pageable);

    // Bulk Update: 대금 지급 완료된 티켓 일괄 플래그 처리
    // clearAutomatically: 벌크 UPDATE 후 1차 캐시 자동 초기화
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.provideFlag = true WHERE t.id IN :ticketIds")
    int bulkMarkProvided(@Param("ticketIds") List<Long> ticketIds);

    // Bulk Delete: 미결제 RESERVED 티켓 일괄 삭제 (미결제 회수)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Ticket t WHERE t.schedule.id = :scheduleId AND t.status = :status")
    int deleteAllByScheduleIdAndStatus(@Param("scheduleId") Long scheduleId, @Param("status") TicketStatus status);

    long countByScheduleIdAndStatus(Long scheduleId, TicketStatus status);
}
