package com.example.movieservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // jpa가 엔티티 만들 때 사용, 외부에서 생성하지 못하게 제한
@AllArgsConstructor(access = AccessLevel.PRIVATE) // builder가 사용, 외부에서 빌더 패턴 사용하도록 강제
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

    @ManyToMany
    @JoinTable(
            name = "movie_category",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default // builder로 만들어도 빈 리스트로 초기화할 수 있게
    private List<Category> categories = new ArrayList<>();

    public enum Visibility {
        PUBLIC, PRIVATE
    }

    public void updateVisibility(Visibility visibility){
        this.visibility = visibility;
    }

    public void addCategory(Category category){
        this.categories.add(category);
    }

    public void updateDetail(String title, String description, Integer additionalCookie){
        this.title = title;
        this.description = description;
        this.additionalCookie = additionalCookie;
    }

    // review.written 수신 시
    public void applyReviewCreated(Integer rating) {
        this.averageRating = (averageRating * reviewCount + rating) / (reviewCount + 1);
        this.reviewCount++;
    }

    // review.updated 수신 시
    public void applyReviewUpdated(Integer oldRating, Integer newRating) {
        this.averageRating = (averageRating * reviewCount - oldRating + newRating) / reviewCount;
    }

    // review.deleted 수신 시
    public void applyReviewDeleted(Integer rating) {
        this.averageRating = reviewCount == 1 ? 0 : (averageRating * reviewCount - rating) / (reviewCount - 1);
        this.reviewCount--;
    }

    // 배치 보정 시
    public void recalculateRating(int reviewCount, float averageRating) {
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
    }
}
