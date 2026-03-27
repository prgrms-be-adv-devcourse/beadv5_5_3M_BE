package com.example.rivewservice.domain.model;

import com.example.rivewservice.domain.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ReviewStatus status;

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "flag")
    private Boolean flag;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "ticket_id")
    private Long ticketId;

    public static Review authorization(User user, Long movieId, Long scheduleId, Long ticketId) {
        Review review = new Review();
        review.user = user;
        review.movieId = movieId;
        review.flag = true;
        review.status = ReviewStatus.AUTHORIZED;
        review.scheduleId = scheduleId;
        review.ticketId = ticketId;
        return  review;
    }

    public void write(String comment, Integer rating) {
        this.comment = comment;
        this.rating = rating;
        this.status = ReviewStatus.WRITTEN;
        this.flag = false;
    }

    public void update(Integer rating, String comment) {
        this.rating = rating;
        this.comment = comment;
        this.status = ReviewStatus.UPDATED;
    }

    public void delete() {
        this.status = ReviewStatus.AUTHORIZED;
        this.flag = true;
        this.comment = null;
        this.rating = null;
    }

}