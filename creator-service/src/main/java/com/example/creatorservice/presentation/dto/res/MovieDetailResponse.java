package com.example.creatorservice.presentation.dto.res;

import com.example.creatorservice.domain.model.Movie;

import java.util.List;
import java.util.UUID;

public record MovieDetailResponse(
        Long movieId,
        UUID creatorId,
        String title,
        String description,
        String imageUrl,
        String videoUrl,
        String visibility,
        Integer runningTime,
        Integer baseCookie,
        Integer additionalCookie,
        Float averageRating,
        Integer reviewCount,
        List<CategoryInfo> categories
) {
    public record CategoryInfo(Long categoryId, String name) {}

    public static MovieDetailResponse from(Movie movie) {
        return new MovieDetailResponse(
                movie.getMovieId(),
                movie.getCreatorId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getImageUrl(),
                movie.getVideoUrl(),
                movie.getVisibility().name(),
                movie.getRunningTime(),
                movie.getBaseCookie(),
                movie.getAdditionalCookie(),
                movie.getAverageRating(),
                movie.getReviewCount(),
                movie.getCategories().stream()
                        .map(c -> new CategoryInfo(c.getCategoryId(), c.getName()))
                        .toList()
        );
    }
}