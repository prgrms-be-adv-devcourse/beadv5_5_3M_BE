package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Review;
import com.example.movieservice.domain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {

    private final ReviewJpaRepository reviewJpaRepository;

    @Override
    public Optional<Review> findById(Long reviewId) {
        return reviewJpaRepository.findById(reviewId);
    }

    @Override
    public void save(Review review) {
        reviewJpaRepository.save(review);
    }

    @Override
    public void delete(Review review) {
        reviewJpaRepository.delete(review);
    }

    @Override
    public List<Review> findTop5ByMovieId(Long movieId) {
        return reviewJpaRepository.findTop5ByMovieIdOrderByUpdatedAtDesc(movieId);
    }
}
