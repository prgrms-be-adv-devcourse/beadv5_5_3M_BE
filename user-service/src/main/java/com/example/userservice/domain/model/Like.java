package com.example.userservice.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long movieId;

    private UUID userId;

    public Like(Long movieId, UUID userId) {
        this.movieId = movieId;
        this.userId = userId;
    }

    public static Like create(Long movieId, UUID userId) {
        return new Like(movieId, userId);
    }
}
