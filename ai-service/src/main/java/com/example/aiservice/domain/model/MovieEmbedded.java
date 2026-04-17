package com.example.aiservice.domain.model;

import com.pgvector.PGvector;
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

    // 차원수는 임베딩 모델 결정 후 DDL에서 확정 (현재 placeholder)
    @Column(name = "embedding", columnDefinition = "vector")
    private PGvector embedding;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category", columnDefinition = "text[]")
    private String[] category;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;
}
