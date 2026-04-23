package com.example.movieservice.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "review",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_review_user_schedule",
        columnNames = {"user_id", "schedule_id"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "rating")
    private Integer rating; // 0~5까지

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReviewStatus status;

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    public enum ReviewStatus {
        CREATE,  // 작성
        UPDATE  // 수정
    }

    public void update(Integer rating, String comment) {
        this.rating = rating;
        this.comment = comment;
        this.updatedAt = LocalDateTime.now();
        this.status = ReviewStatus.UPDATE;
    }
}