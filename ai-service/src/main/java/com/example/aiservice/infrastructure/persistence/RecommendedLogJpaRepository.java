package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.RecommendedLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecommendedLogJpaRepository extends JpaRepository<RecommendedLog, Long> {

    @Modifying
    @Query(
            value = "INSERT INTO recommended_log (user_id, movie_id, is_clicked, is_exploration, exploration_source, recommended_at) " +
                    "VALUES (:userId, :movieId, false, :isExploration, :explorationSource, :recommendedAt) " +
                    "ON CONFLICT (user_id, movie_id, recommended_at) DO NOTHING",
            nativeQuery = true
    )
    void insertOnConflictDoNothing(
            @Param("userId") UUID userId,
            @Param("movieId") Long movieId,
            @Param("isExploration") boolean isExploration,
            @Param("explorationSource") String explorationSource,
            @Param("recommendedAt") LocalDate recommendedAt
    );

    @Query("SELECT l FROM RecommendedLog l WHERE l.userId = :userId AND l.movieId IN :movieIds AND l.recommendedAt = :date")
    List<RecommendedLog> findByUserIdAndMovieIdsAndDate(
            @Param("userId") UUID userId,
            @Param("movieIds") List<Long> movieIds,
            @Param("date") LocalDate date
    );

    @Modifying
    @Query("UPDATE RecommendedLog l SET l.isClicked = true WHERE l.id = :logId AND l.userId = :userId")
    int markAsClicked(@Param("logId") Long logId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM RecommendedLog l WHERE l.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
