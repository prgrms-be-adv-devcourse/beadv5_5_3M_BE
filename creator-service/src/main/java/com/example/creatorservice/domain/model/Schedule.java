package com.example.creatorservice.domain.model;

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
        SCHEDULED,
        WAITING,
        ON_AIR,
        COMPLETED
    }

    public static Schedule create(LocalDateTime ticketingTime, LocalDateTime startTime,
                                   LocalDateTime endTime, Movie movie) {
        return Schedule.builder()
                .ticketingTime(ticketingTime)
                .startTime(startTime)
                .endTime(endTime)
                .isConfirmed(false)
                .remainingSeats(100)
                .movie(movie)
                .build();
    }

    public void confirm() {
        this.isConfirmed = true;
    }

    public void scheduled() {
        this.status = ScheduleStatus.SCHEDULED;
    }

    public void waiting() {
        this.status = ScheduleStatus.WAITING;
    }

    public void start() {
        this.status = ScheduleStatus.ON_AIR;
    }

    public void complete() {
        this.status = ScheduleStatus.COMPLETED;
    }
}