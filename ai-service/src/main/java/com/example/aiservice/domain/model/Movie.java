package com.example.aiservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Movie {

    @Id
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "average_rating")
    private Float averageRating;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category", columnDefinition = "text[]")
    private String[] category;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "creator_id")
    private UUID creatorId;
}
