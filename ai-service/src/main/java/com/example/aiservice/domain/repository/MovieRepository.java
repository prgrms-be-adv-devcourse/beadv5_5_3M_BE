package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.Movie;

public interface MovieRepository {

    boolean existsById(Long movieId);

    Movie save(Movie movie);

    void deleteById(Long movieId);
}
