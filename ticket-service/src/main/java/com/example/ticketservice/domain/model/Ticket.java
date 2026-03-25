package com.example.ticketservice.domain.model;


import com.example.ticketservice.common.exception.TicketAlreadyCancelledException;
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
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @ManyToOne
    @JoinColumn(name = "schedule_id", insertable = false, updatable = false)
    private Schedule schedule;

    public static Ticket create(UUID userId, Schedule schedule) {
        Ticket ticket = new Ticket();
        ticket.userId = userId;
        ticket.schedule = schedule;
        ticket.status = TicketStatus.RESERVED;
        ticket.provideFlag = false;
        return ticket;
    }

    public void confirm() {
        if (this.status != TicketStatus.RESERVED) {
            throw new IllegalStateException("RESERVED 상태의 티켓만 확정할 수 있습니다.");
        }
        this.status = TicketStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status == TicketStatus.CANCELLED) {
            throw new TicketAlreadyCancelledException(this.id);
        }
        if (this.status == TicketStatus.CONFIRMED){
            throw new IllegalStateException("이미 확정된 티켓은 취소할 수 없습니다.");
        }
        this.status = TicketStatus.CANCELLED;
    }

    public void markProvided() {
        this.provideFlag = true;
    }
}