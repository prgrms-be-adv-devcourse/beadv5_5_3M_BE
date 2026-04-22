package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.response.movie.*;

import java.util.List;
import java.util.UUID;

public interface MovieUseCase {

    DetailForUserResponse getDetailForUser(Long movieId);

    List<MovieByCreatorResponse> getMovieListByCreator(UUID creatorId);

    List<MovieCardResponse> getOnAirMovieList();

    List<ScheduledMovieResponse> getScheduledMovieList();

    List<MovieCardResponse> getPublicMovieList();

    List<MovieCardResponse> getMovieListByGenre(Long categoryId);

    List<MovieCardResponse> searchMoviesByTitle(String title);
}