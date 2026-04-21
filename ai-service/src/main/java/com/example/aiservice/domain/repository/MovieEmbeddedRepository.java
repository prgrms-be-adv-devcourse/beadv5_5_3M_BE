package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.MovieEmbedded;

public interface MovieEmbeddedRepository {

    long count();

    boolean existsById(Long movieId);

    MovieEmbedded findById(Long movieId);

    MovieEmbedded save(MovieEmbedded movieEmbedded);

    void deleteById(Long movieId);

    void publishMovie(Long movieId);

    void unpublishMovie(Long movieId);
}
