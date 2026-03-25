package com.example.movieservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movie_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "video_url", length = 255)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 10)
    private Visibility visibility;

    @Column(name = "running_time")
    private Integer runningTime; // sec 단위

    @Column(name = "base_cookie")
    private Integer baseCookie;

    @Column(name = "additional_cookie")
    private Integer additionalCookie;

    @Column(name = "average_rating")
    private Float averageRating;

    @Column(name = "review_count")
    private Integer reviewCount;

    public enum Visibility {
        PUBLIC, PRIVATE
    }
}
