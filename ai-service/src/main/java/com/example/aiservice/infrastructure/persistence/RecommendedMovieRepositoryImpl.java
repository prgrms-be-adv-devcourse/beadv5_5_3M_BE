package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.RecommendedMovie;
import com.example.aiservice.domain.repository.RecommendedMovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecommendedMovieRepositoryImpl implements RecommendedMovieRepository {

    private final RecommendedMovieJpaRepository recommendedMovieJpaRepository;

    @Override
    public List<RecommendedMovie> findTop10ByUserIdOrderByRank(UUID userId) {
        return recommendedMovieJpaRepository.findTop10ByUserIdOrderByRank(userId);
    }

    @Override
    public List<UUID> findUserIdsByMovieId(Long movieId) {
        return recommendedMovieJpaRepository.findUserIdsByMovieId(movieId);
    }

    @Override
    public void deleteByMovieId(Long movieId) {
        recommendedMovieJpaRepository.deleteByMovieId(movieId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        recommendedMovieJpaRepository.deleteByUserId(userId);
    }
}
