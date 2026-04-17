package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.ExplorationSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommended_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecommendedLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "is_clicked", nullable = false)
    private boolean isClicked;

    @Column(name = "is_exploration", nullable = false)
    private boolean isExploration;

    @Enumerated(EnumType.STRING)
    @Column(name = "exploration_source", length = 20)
    private ExplorationSource explorationSource;

    @Column(name = "recommended_at", nullable = false)
    private LocalDateTime recommendedAt;
}
