package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.MovieEmbedded;

public interface MovieEmbeddedRepository {

    MovieEmbedded save(MovieEmbedded movieEmbedded);

    void deleteById(Long movieId);
}
