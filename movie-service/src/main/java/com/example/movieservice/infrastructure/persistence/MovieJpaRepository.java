package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
    long countByCreatorId(UUID creatorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Movie m WHERE m.movieId = :movieId")
    Optional<Movie> findByMovieIdForUpdate(@Param("movieId") Long movieId);

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.categories WHERE m.creatorId = :creatorId")
    List<Movie> findAllByCreatorId(@Param("creatorId") UUID creatorId);

    @Query("SELECT m.movieId FROM Movie m")
    List<Long> findAllMovieIds();

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.categories WHERE m.visibility = 'PUBLIC'")
    List<Movie> findAllByVisibilityPublic();

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.categories " +
            "WHERE m.visibility = 'PUBLIC' " +
            "AND EXISTS (SELECT 1 FROM m.categories c WHERE c.categoryId = :categoryId)")
    List<Movie> findAllByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.categories WHERE m.visibility = 'PUBLIC' AND m.title LIKE %:title%")
    List<Movie> searchByTitle(@Param("title") String title);
}
