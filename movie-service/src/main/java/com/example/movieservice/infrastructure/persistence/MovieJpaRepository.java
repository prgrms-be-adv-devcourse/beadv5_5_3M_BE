package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
    long countByCreatorId(UUID creatorId);

    List<Movie> findAllByCreatorId(UUID creatorId);

    @Query("SELECT m.movieId FROM Movie m")
    List<Long> findAllMovieIds();
}
