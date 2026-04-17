package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieEmbedded;
import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MovieEmbeddedRepositoryImpl implements MovieEmbeddedRepository {

    private final MovieEmbeddedJpaRepository movieEmbeddedJpaRepository;

    @Override
    public boolean existsById(Long movieId) {
        return movieEmbeddedJpaRepository.existsById(movieId);
    }

    @Override
    public MovieEmbedded save(MovieEmbedded movieEmbedded) {
        return movieEmbeddedJpaRepository.save(movieEmbedded);
    }

    @Override
    public void deleteById(Long movieId) {
        movieEmbeddedJpaRepository.deleteById(movieId);
    }
}
