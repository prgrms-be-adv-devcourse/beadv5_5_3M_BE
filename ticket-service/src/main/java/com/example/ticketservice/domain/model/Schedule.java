package com.example.ticketservice.domain.model;


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

    @OneToMany(mappedBy = "schedule")
    private List<Ticket> tickets = new ArrayList<>();

    public static Schedule create(Long id, LocalDateTime startTime, LocalDateTime endTime,
                                  String title, Integer cookie, UUID creatorId,
                                  Long movieId, String imageUrl, Integer seats) {
        Schedule s = new Schedule();
        s.id = id;
        s.startTime = startTime;
        s.endTime = endTime;
        s.title = title;
        s.cookie = cookie;
        s.creatorId = creatorId;
        s.movieId = movieId;
        s.imageUrl = imageUrl;
        s.seats = seats;
        return s;
    }

    public void decreaseSeats() {
        this.seats--;
    }

    public void increaseSeats() {
        this.seats++;
    }
}