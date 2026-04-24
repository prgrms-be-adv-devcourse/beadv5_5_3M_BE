package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.ExplorationSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommended_movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecommendedMovie {

    @EmbeddedId
    private RecommendedMovieId id;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "is_exploration", nullable = false)
    private boolean isExploration;

    @Enumerated(EnumType.STRING)
    @Column(name = "exploration_source", length = 20)
    private ExplorationSource explorationSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
