package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.InteractionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_interaction_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserInteractionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", length = 10, nullable = false)
    private InteractionType interactionType;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
