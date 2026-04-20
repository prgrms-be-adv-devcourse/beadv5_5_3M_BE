package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.MovieLike;

import java.util.Optional;
import java.util.UUID;

public interface MovieLikeRepository {
    void save(MovieLike movieLike);
    Optional<MovieLike> findByUserIdAndMovieId(UUID userId, Long movieId);
    void delete(MovieLike movieLike);
    boolean existsByUserIdAndMovieId(UUID userId, Long movieId);
}