package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieStatistics;
import com.example.aiservice.domain.model.MovieStatisticsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieStatisticsJpaRepository extends JpaRepository<MovieStatistics, MovieStatisticsId> {

    @Modifying
    @Query("DELETE FROM MovieStatistics s WHERE s.id.movieId = :movieId")
    void deleteByMovieId(@Param("movieId") Long movieId);
}
