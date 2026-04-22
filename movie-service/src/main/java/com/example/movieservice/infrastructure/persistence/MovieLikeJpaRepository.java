package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.MovieLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MovieLikeJpaRepository extends JpaRepository<MovieLike, Long> {
    Optional<MovieLike> findByUserIdAndMovieId(UUID userId, Long movieId);
    boolean existsByUserIdAndMovieId(UUID userId, Long movieId);
    int deleteByUserIdAndMovieId(UUID userId, Long movieId);
}