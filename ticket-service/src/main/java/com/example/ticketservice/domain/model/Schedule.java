package com.example.ticketservice.domain.model;


import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    private Long id;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "ticketing_time")
    private LocalDateTime ticketingTime;

    @Column(name = "title")
    private String title;

    @Column(name = "cookie")
    private Integer cookie;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "seats")
    private Integer seats;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    @OneToMany(mappedBy = "schedule")
    private List<Ticket> tickets = new ArrayList<>();

    public static Schedule create(Long id, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime ticketingTime,
                                  String title, Integer cookie, UUID creatorId,
                                  Long movieId, String imageUrl, Integer seats) {
        Schedule s = new Schedule();
        s.id = id;
        s.startTime = startTime;
        s.endTime = endTime;
        s.ticketingTime = ticketingTime;
        s.status = ScheduleStatus.CART;
        s.title = title;
        s.cookie = cookie;
        s.creatorId = creatorId;
        s.movieId = movieId;
        s.imageUrl = imageUrl;
        s.seats = seats;
        return s;
    }

    // 장바구니 마감: CART → IN_PROGRESSING
    public void closeCart() {
        if (this.status != ScheduleStatus.CART) {
            throw ScheduleErrorCode.NOT_IN_CART_PERIOD.of(this.id);
        }
        this.status = ScheduleStatus.IN_PROGRESSING;
    }

    // 티켓팅 시작: IN_PROGRESSING → TICKETING
    public void startTicketing() {
        if (this.status != ScheduleStatus.IN_PROGRESSING) {
            throw ScheduleErrorCode.CART_CLOSED.of(this.id);
        }
        this.status = ScheduleStatus.TICKETING;
    }

    // 스트리밍 시작: TICKETING → STREAMING
    public void startStreaming() {
        if (this.status != ScheduleStatus.TICKETING) {
            throw ScheduleErrorCode.NOT_IN_TICKETING.of(this.id);
        }
        this.status = ScheduleStatus.STREAMING;
    }

    // 스트리밍 종료: STREAMING → FINISH
    public void finishStreaming() {
        if (this.status != ScheduleStatus.STREAMING) {
            throw ScheduleErrorCode.NOT_IN_STREAMING.of(this.id);
        }
        this.status = ScheduleStatus.FINISH;
    }

}