package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.MovieLike;
import com.example.movieservice.domain.repository.MovieLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MovieLikeRepositoryAdapter implements MovieLikeRepository {

    private final MovieLikeJpaRepository jpaRepository;

    @Override
    public void save(MovieLike movieLike) {
        jpaRepository.save(movieLike);
    }

    @Override
    public Optional<MovieLike> findByUserIdAndMovieId(UUID userId, Long movieId) {
        return jpaRepository.findByUserIdAndMovieId(userId, movieId);
    }

    @Override
    public int deleteByUserIdAndMovieId(UUID userId, Long movieId) {
        return jpaRepository.deleteByUserIdAndMovieId(userId, movieId);
    }

    @Override
    public boolean existsByUserIdAndMovieId(UUID userId, Long movieId) {
        return jpaRepository.existsByUserIdAndMovieId(userId, movieId);
    }
}