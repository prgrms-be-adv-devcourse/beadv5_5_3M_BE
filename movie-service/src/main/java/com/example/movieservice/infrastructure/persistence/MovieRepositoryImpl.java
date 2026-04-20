package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    @Override
    public List<Movie> findMoviesByCreatorId(UUID creatorId){
        return movieJpaRepository.findAllByCreatorId(creatorId);
    }

    @Override
    public List<Long> findAllMovieIds() {
        return movieJpaRepository.findAllMovieIds();
    }

    @Override
    public List<Movie> findAllByMovieIds(Collection<Long> movieIds) {
        return movieJpaRepository.findAllById(movieIds);
    }

    @Override
    public List<Movie> findAllPublic() {
        return movieJpaRepository.findAllByVisibilityPublic();
    }

    @Override
    public List<Movie> findAllByCategoryId(Long categoryId) {
        return movieJpaRepository.findAllByCategoryId(categoryId);
    }

    @Override
    public List<Movie> searchByTitle(String title) {
        return movieJpaRepository.searchByTitle(title);
    }
}
