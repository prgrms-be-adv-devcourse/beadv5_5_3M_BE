package com.example.creatorservice.presentation.dto.res;

import com.example.creatorservice.domain.model.Movie;

public record SchedulableMovieResponse(
        Long movieId,
        String title,
        String imageUrl,
        Integer runningTime,
        Integer baseCookie,
        Integer additionalCookie
) {
    public static SchedulableMovieResponse from(Movie movie) {
        return new SchedulableMovieResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getImageUrl(),
                movie.getRunningTime(),
                movie.getBaseCookie(),
                movie.getAdditionalCookie()
        );
    }
}