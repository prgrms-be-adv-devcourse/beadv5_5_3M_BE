package com.example.creatorservice.domain.repository;

import com.example.creatorservice.domain.model.Movie;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovieRepository {
    Movie save(Movie movie);
    Optional<Movie> findById(Long movieId);
    void delete(Movie movie);
    long countByCreatorId(UUID creatorId);
    List<Movie> findAllByCreatorId(UUID creatorId);
    boolean existsConfirmedScheduleByMovieId(Long movieId);
}