package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.domain.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
    long countByCreatorId(UUID creatorId);
    List<Movie> findAllByCreatorId(UUID creatorId);

    @Query("SELECT COUNT(s) > 0 FROM Schedule s WHERE s.movie.movieId = :movieId AND s.isConfirmed = true")
    boolean existsConfirmedScheduleByMovieId(@Param("movieId") Long movieId);

}