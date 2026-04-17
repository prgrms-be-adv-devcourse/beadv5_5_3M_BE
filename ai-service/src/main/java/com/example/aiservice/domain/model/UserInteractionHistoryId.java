package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.InteractionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserInteractionHistoryId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "movie_id")
    private Long movieId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", length = 10)
    private InteractionType interactionType;
}
