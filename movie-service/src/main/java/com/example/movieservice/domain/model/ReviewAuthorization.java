package com.example.movieservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_authorization",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ticket_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReviewAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @CreationTimestamp
    @Column(name = "authorized_at", updatable = false)
    private LocalDateTime authorizedAt;
}