package com.example.creatorservice.presentation.dto.res;

import com.example.creatorservice.domain.model.Movie;

import java.time.LocalDateTime;

public record MovieListItemResponse(
        Long movieId,
        String title,
        String imageUrl,
        String visibility,
        Integer runningTime,
        Float averageRating,
        Integer reviewCount,
        LocalDateTime createdAt
) {
    public static MovieListItemResponse from(Movie movie) {
        return new MovieListItemResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getImageUrl(),
                movie.getVisibility().name(),
                movie.getRunningTime(),
                movie.getAverageRating(),
                movie.getReviewCount(),
                movie.getCreatedAt()
        );
    }
}