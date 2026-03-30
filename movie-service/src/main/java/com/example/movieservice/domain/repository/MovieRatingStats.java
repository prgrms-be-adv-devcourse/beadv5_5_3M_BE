package com.example.movieservice.domain.repository;

public interface MovieRatingStats {
    Long getMovieId();
    Long getReviewCount();
    Double getAverageRating();
}
