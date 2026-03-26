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
@Table(name = "tickets")
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

    // 스케줄 확정 시 좌석 수만큼 AVAILABLE 티켓을 미리 생성
    public static Ticket create(Schedule schedule, int ticketNum) {
        Ticket ticket = new Ticket();
        ticket.schedule = schedule;
        ticket.ticketNum = ticketNum;
        ticket.status = TicketStatus.AVAILABLE;
        ticket.provideFlag = false;
        return ticket;
    }

    // 예매: AVAILABLE → RESERVED
    public void reserved(UUID userId) {
        switch (this.status) {
            case RESERVED -> throw TicketErrorCode.ALREADY_RESERVED.of(this.id);
            case CONFIRMED -> throw TicketErrorCode.ALREADY_CONFIRMED.of(this.id);
            case HOLD -> throw TicketErrorCode.ALREADY_HOLD.of(this.id);
            default -> {
                this.userId = userId;
                this.status = TicketStatus.RESERVED;
            }
        }
    }

    // 취소: RESERVED → AVAILABLE (티켓 풀로 반환)
    public void cancel() {
        switch (this.status) {
            case AVAILABLE -> throw TicketErrorCode.NOT_RESERVED.of(this.id);
            case CONFIRMED -> throw TicketErrorCode.ALREADY_CONFIRMED.of(this.id);
            case HOLD -> throw TicketErrorCode.ALREADY_HOLD.of(this.id);
            default -> {
                this.userId = null;
                this.status = TicketStatus.AVAILABLE;
            }
        }
    }

    // 확정: RESERVED → CONFIRMED (배치 처리)
    public void confirm() {
        if (this.status != TicketStatus.RESERVED) {
            throw TicketErrorCode.NOT_RESERVED.of(this.id);
        }
        this.status = TicketStatus.CONFIRMED;
    }

    // 판매 중지 (관리자)
    public void hold() {
        if (this.status == TicketStatus.HOLD) {
            throw TicketErrorCode.ALREADY_HOLD.of(this.id);
        }
        this.status = TicketStatus.HOLD;
    }

    public void markProvided() {
        this.provideFlag = true;
    }
}