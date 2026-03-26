package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Movie;

import java.util.Optional;
import java.util.UUID;

public interface MovieRepository {
    Movie save(Movie movie);

    long countByCreatorId(UUID creatorId);

    Optional<Movie> findByMovieId(Long movieId);

    void delete(Movie movie);
}
