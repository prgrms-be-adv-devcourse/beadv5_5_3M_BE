package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.Movie;
import com.example.aiservice.domain.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MovieRepositoryImpl implements MovieRepository {

    private final MovieJpaRepository movieJpaRepository;

    @Override
    public boolean existsById(Long movieId) {
        return movieJpaRepository.existsById(movieId);
    }

    @Override
    public Movie save(Movie movie) {
        return movieJpaRepository.save(movie);
    }

    @Override
    public void deleteById(Long movieId) {
        movieJpaRepository.deleteById(movieId);
    }
}
