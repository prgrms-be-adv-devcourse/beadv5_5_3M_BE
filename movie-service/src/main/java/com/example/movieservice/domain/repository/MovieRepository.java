package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Movie;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovieRepository {
    Movie save(Movie movie);

    long countByCreatorId(UUID creatorId);

    Optional<Movie> findByMovieId(Long movieId);

    void delete(Movie movie);

    List<Movie> findMoviesByCreatorId(UUID creatorId);

    List<Long> findAllMovieIds();

    List<Movie> findAllByMovieIds(Collection<Long> movieIds);

    List<Movie> findAllPublic();

    List<Movie> findAllByCategoryId(Long categoryId);

    List<Movie> searchByTitle(String title);
}
