package com.example.creatorservice.domain.model;

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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "video_url", length = 255)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 10, nullable = false)
    private Visibility visibility;

    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "base_cookie", nullable = false)
    private Integer baseCookie;

    @Column(name = "additional_cookie", nullable = false)
    private Integer additionalCookie;

    @Column(name = "average_rating")
    private Float averageRating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "ever_published", nullable = false)
    @Builder.Default
    private boolean everPublished = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany
    @JoinTable(
            name = "movie_category",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private List<Category> categories = new ArrayList<>();

    public enum Visibility {
        PUBLIC, PRIVATE
    }

    public static Movie create(UUID creatorId, String title, String description,
                               Integer baseCookie, Integer additionalCookie,
                               Integer runningTime, String imageUrl, String videoUrl) {
        return Movie.builder()
                .creatorId(creatorId)
                .title(title)
                .description(description)
                .baseCookie(baseCookie)
                .additionalCookie(additionalCookie)
                .runningTime(runningTime)
                .imageUrl(imageUrl)
                .videoUrl(videoUrl)
                .visibility(Visibility.PRIVATE)
                .averageRating(0.0f)
                .reviewCount(0)
                .build();
    }

    public void updateVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public void markAsPublished() {
        this.everPublished = true;
    }

    public void updateDetail(String title, String description, Integer additionalCookie) {
        this.title = title;
        this.description = description;
        this.additionalCookie = additionalCookie;
    }

    public void addCategory(Category category) {
        this.categories.add(category);
    }

    public void replaceCategories(List<Category> newCategories) {
        this.categories.clear();
        this.categories.addAll(newCategories);
    }

    public boolean isPublic() {
        return this.visibility == Visibility.PUBLIC;
    }
}