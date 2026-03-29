package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
    long countByCreatorId(UUID creatorId);

    List<Movie> findAllByCreatorId(UUID creatorId);

    @Query("SELECT m.movieId FROM Movie m")
    List<Long> findAllMovieIds();

    @Query("SELECT m FROM Movie m LEFT JOIN FETCH m.categories WHERE m.visibility = 'PUBLIC'")
    List<Movie> findAllByVisibilityPublic();

    @Query("SELECT m FROM Movie m LEFT JOIN FETCH m.categories " +
            "WHERE m.visibility = 'PUBLIC' " +
            "AND EXISTS (SELECT 1 FROM m.categories c WHERE c.categoryId = :categoryId)")
    List<Movie> findAllByCategoryId(@Param("categoryId") Long categoryId);
}
