package com.example.aiservice.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_interaction_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserInteractionHistory {

    @EmbeddedId
    private UserInteractionHistoryId id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
