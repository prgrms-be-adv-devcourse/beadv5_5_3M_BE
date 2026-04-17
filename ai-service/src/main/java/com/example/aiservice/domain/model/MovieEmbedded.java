package com.example.aiservice.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "movies_embedded")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MovieEmbedded {

    @Id
    @Column(name = "movie_id")
    private Long movieId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category", columnDefinition = "text[]")
    private String[] category;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;
}
