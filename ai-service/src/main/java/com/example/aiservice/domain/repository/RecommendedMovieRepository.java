package com.example.aiservice.domain.repository;

import java.util.List;
import java.util.UUID;

public interface RecommendedMovieRepository {

    List<UUID> findUserIdsByMovieId(Long movieId);

    void deleteByMovieId(Long movieId);

    void deleteByUserId(UUID userId);
}
