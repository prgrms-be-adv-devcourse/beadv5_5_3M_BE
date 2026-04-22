package com.example.movieservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movie_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MovieLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @CreationTimestamp
    @Column(name = "liked_at", updatable = false)
    private LocalDateTime likedAt;
}