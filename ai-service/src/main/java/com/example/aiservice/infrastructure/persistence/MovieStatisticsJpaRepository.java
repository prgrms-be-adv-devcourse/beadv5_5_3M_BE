package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieStatistics;
import com.example.aiservice.domain.model.MovieStatisticsId;
import com.example.aiservice.domain.model.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieStatisticsJpaRepository extends JpaRepository<MovieStatistics, MovieStatisticsId> {

    @Modifying
    @Query("DELETE FROM MovieStatistics s WHERE s.id.movieId = :movieId")
    void deleteByMovieId(@Param("movieId") Long movieId);

    @Modifying
    @Query("UPDATE MovieStatistics s SET s.watchCount = s.watchCount + 1 WHERE s.id.movieId = :movieId AND s.id.ageGroup = :ageGroup AND s.id.gender = :gender")
    void incrementWatchCount(@Param("movieId") Long movieId, @Param("ageGroup") int ageGroup, @Param("gender") Gender gender);

    // 인구통계 기반 후보: 연령/성별 조합에서 (해당 인구통계 시청수 / 영화 전체 시청수) 비율 상위
    // Popularity Bias 방지를 위해 전체 시청수 대비 비율로 정규화
    @Query(value = """
            SELECT ms.movie_id
            FROM movie_statistics ms
            JOIN movies_embedded me ON ms.movie_id = me.movie_id
            JOIN (
                SELECT movie_id, SUM(watch_count) AS total_watch
                FROM movie_statistics
                GROUP BY movie_id
            ) totals ON ms.movie_id = totals.movie_id
            WHERE ms.age_group = :ageGroup
              AND ms.gender = :gender
              AND me.is_public = true
            ORDER BY ms.watch_count::float / NULLIF(totals.total_watch, 0) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDemographicCandidates(
            @Param("ageGroup") int ageGroup,
            @Param("gender") String gender,
            @Param("limit") int limit);

    // 노출 하위 후보: 전체 시청수 합산이 낮은 영화 (Long Tail 노출)
    @Query(value = """
            SELECT stats.movie_id
            FROM (
                SELECT movie_id, SUM(watch_count) AS total_watch
                FROM movie_statistics
                GROUP BY movie_id
            ) stats
            JOIN movies_embedded me ON stats.movie_id = me.movie_id
            WHERE me.is_public = true
            ORDER BY stats.total_watch ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findLowExposureCandidates(@Param("limit") int limit);

}
