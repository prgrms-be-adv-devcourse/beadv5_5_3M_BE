package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.UpdateDetailRequest;
import com.example.movieservice.presentation.dto.request.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.*;

import java.util.List;
import java.util.UUID;

public interface MovieUseCase {
    RegisterMovieResponse register(UUID creatorId, RegisterMovieRequest request);

    void updateVisibility(UUID creatorId, Long movieId, UpdateVisibilityRequest request);

    void updateDetail(UUID creatorId, Long movieId, UpdateDetailRequest request);

    void delete(UUID creatorId, Long movieId);

    DetailForCreatorResponse getDetailForCreator(UUID creatorId, Long movieId);

    DetailForUserResponse getDetailForUser(UUID userId, Long movieId);

    List<MovieByCreatorResponse> getMovieListByCreator(UUID creatorId);

    List<MovieForCreatorResponse> getMovieListForCreator(UUID creatorId);

    List<MovieForScheduleResponse> getPublicMovieListForSchedule(UUID creatorId);
}
