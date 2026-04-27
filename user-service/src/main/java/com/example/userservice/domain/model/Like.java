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

    private String imageUrl;

    private String title;

    private UUID userId;

    public Like(Long movieId, UUID userId, String imageUrl, String title) {
        this.movieId = movieId;
        this.userId = userId;
        this.imageUrl = imageUrl;
        this.title = title;
    }

    public static Like create(Long movieId, UUID userId, String imageUrl, String title) {
        return new Like(movieId, userId, imageUrl, title);
    }
}
