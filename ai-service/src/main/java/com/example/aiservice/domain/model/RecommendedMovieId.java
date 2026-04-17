package com.example.aiservice.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RecommendedMovieId implements Serializable {

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "user_id")
    private UUID userId;
}
