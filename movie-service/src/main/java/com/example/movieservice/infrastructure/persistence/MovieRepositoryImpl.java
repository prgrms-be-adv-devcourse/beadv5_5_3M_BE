package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MovieRepositoryImpl implements MovieRepository {
    private final MovieJpaRepository movieJpaRepository;

    @Override
    public Movie save(Movie movie) {
        return movieJpaRepository.save(movie);
    }

    @Override
    public long countByCreatorId(UUID creatorId) {
        return movieJpaRepository.countByCreatorId(creatorId);
    }

    @Override
    public Optional<Movie> findByMovieId(Long movieId) {
        return movieJpaRepository.findById(movieId);
    }

    @Override
    public void delete(Movie movie) {
        movieJpaRepository.delete(movie);
    }


}
