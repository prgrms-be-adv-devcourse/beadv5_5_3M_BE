package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.RecommendedMovie;

import java.util.List;
import java.util.UUID;

public interface RecommendedMovieRepository {

    List<RecommendedMovie> findTop10ByUserIdOrderByRank(UUID userId);

    List<UUID> findUserIdsByMovieId(Long movieId);

    void deleteByMovieId(Long movieId);

    void deleteByUserId(UUID userId);
}
