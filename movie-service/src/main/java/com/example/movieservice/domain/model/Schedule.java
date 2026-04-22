package com.example.movieservice.domain.model;

import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "ticketing_time", nullable = false)
    private LocalDateTime ticketingTime;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "is_confirmed", nullable = false)
    private Boolean isConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.NOT_CONFIRMED;

    @Column(name = "total_seats", nullable = false)
    @Builder.Default
    private Integer totalSeats = 100;

    @Column(name = "remaining_seats", nullable = false)
    private Integer remainingSeats;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    public enum ScheduleStatus {
        NOT_CONFIRMED,
        SCHEDULED,   // 상영 예정
        WAITING,     // 상영 대기
        ON_AIR,      // 상영중
        COMPLETED    // 상영 완료
    }

    // 도메인 메서드
    public void confirm() {
        this.isConfirmed = true;
    }

    public void scheduled() { this.status = ScheduleStatus.SCHEDULED; }

    public void waiting(){
        this.status = ScheduleStatus.WAITING;
    }

    public void start() {
        this.status = ScheduleStatus.ON_AIR;
    }

    public void complete() {
        this.status = ScheduleStatus.COMPLETED;
    }

    public void decreaseRemainingSeats() {
        if (remainingSeats <= 0) throw new GeneralException(ErrorStatus.SCHEDULE_NO_REMAINING_SEATS);
        this.remainingSeats--;
    }

    // 반드시 PESSIMISTIC_WRITE 락 하에서 호출되어야 함 — 락 없이 호출 시 동시성 안전 보장 불가
    public void increaseRemainingSeats() {
        if (remainingSeats >= totalSeats) throw new GeneralException(ErrorStatus.SCHEDULE_EXCEEDS_TOTAL_SEATS);
        this.remainingSeats++;
    }
}
