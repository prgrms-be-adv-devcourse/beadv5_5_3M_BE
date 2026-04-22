package com.example.ticketservice.domain.model;

import com.example.ticketservice.common.exception.TicketErrorCode;
import com.example.ticketservice.domain.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "tickets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_user_schedule", columnNames = {"user_id", "schedule_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_num")
    private Integer ticketNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TicketStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "user_id")
    private UUID userId;

    // 대금 지급 완료 여부
    @Column(name = "provide_flag")
    private boolean provideFlag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    // 장바구니 마감(Case A) 또는 대기열 구매 시 RESERVED 상태로 직접 생성
    public static Ticket createReserved(Schedule schedule, int ticketNum, UUID userId) {
        Ticket ticket = new Ticket();
        ticket.schedule = schedule;
        ticket.ticketNum = ticketNum;
        ticket.userId = userId;
        ticket.status = TicketStatus.RESERVED;
        ticket.provideFlag = false;
        return ticket;
    }

    // 자율결제 또는 대기열 구매 완료: RESERVED → CONFIRMED
    public void pay() {
        if (this.status != TicketStatus.RESERVED) {
            throw TicketErrorCode.NOT_RESERVED.of(this.id);
        }
        this.status = TicketStatus.CONFIRMED;
    }

}