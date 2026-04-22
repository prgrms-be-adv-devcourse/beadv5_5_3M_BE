package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieEmbedded;
import com.example.aiservice.domain.repository.MovieEmbeddedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MovieEmbeddedRepositoryImpl implements MovieEmbeddedRepository {

    private final MovieEmbeddedJpaRepository movieEmbeddedJpaRepository;

    @Override
    public long count() {
        return movieEmbeddedJpaRepository.count();
    }

    @Override
    public List<MovieEmbedded> findAllByIds(Collection<Long> movieIds) {
        return movieEmbeddedJpaRepository.findAllById(movieIds);
    }

    @Override
    public boolean existsById(Long movieId) {
        return movieEmbeddedJpaRepository.existsById(movieId);
    }

    @Override
    public MovieEmbedded findById(Long movieId) {
        return movieEmbeddedJpaRepository.findById(movieId).orElse(null);
    }

    @Override
    public MovieEmbedded save(MovieEmbedded movieEmbedded) {
        return movieEmbeddedJpaRepository.save(movieEmbedded);
    }

    @Override
    public void deleteById(Long movieId) {
        movieEmbeddedJpaRepository.deleteById(movieId);
    }

    @Override
    public void publishMovie(Long movieId) {
        movieEmbeddedJpaRepository.setPublic(movieId);
        movieEmbeddedJpaRepository.setPublishedAtIfAbsent(movieId);
    }

    @Override
    public void unpublishMovie(Long movieId) {
        movieEmbeddedJpaRepository.unpublishMovie(movieId);
    }
}
