package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.RecommendedMovie;
import com.example.aiservice.domain.model.RecommendedMovieId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommendedMovieJpaRepository extends JpaRepository<RecommendedMovie, RecommendedMovieId> {

    @Query("SELECT r FROM RecommendedMovie r WHERE r.id.userId = :userId ORDER BY r.rank ASC LIMIT 10")
    List<RecommendedMovie> findTop10ByUserIdOrderByRank(@Param("userId") UUID userId);

    @Query("SELECT r.id.userId FROM RecommendedMovie r WHERE r.id.movieId = :movieId")
    List<UUID> findUserIdsByMovieId(@Param("movieId") Long movieId);

    @Modifying
    @Query("DELETE FROM RecommendedMovie r WHERE r.id.movieId = :movieId")
    void deleteByMovieId(@Param("movieId") Long movieId);

    @Modifying
    @Query("DELETE FROM RecommendedMovie r WHERE r.id.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
