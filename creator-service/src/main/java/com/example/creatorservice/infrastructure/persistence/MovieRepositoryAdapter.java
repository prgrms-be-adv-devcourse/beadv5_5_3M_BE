package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.domain.model.Movie;
import com.example.creatorservice.domain.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MovieRepositoryAdapter implements MovieRepository {

    private final MovieJpaRepository movieJpaRepository;

    @Override
    public Movie save(Movie movie) {
        return movieJpaRepository.save(movie);
    }

    @Override
    public Optional<Movie> findById(Long movieId) {
        return movieJpaRepository.findById(movieId);
    }

    @Override
    public void delete(Movie movie) {
        movieJpaRepository.delete(movie);
    }

    @Override
    public long countByCreatorId(UUID creatorId) {
        return movieJpaRepository.countByCreatorId(creatorId);
    }

    @Override
    public List<Movie> findAllByCreatorId(UUID creatorId) {
        return movieJpaRepository.findAllByCreatorId(creatorId);
    }

    @Override
    public boolean existsConfirmedScheduleByMovieId(Long movieId) {
        return movieJpaRepository.existsConfirmedScheduleByMovieId(movieId);
    }

}