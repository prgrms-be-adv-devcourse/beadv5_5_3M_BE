package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.UpdateDetailRequest;
import com.example.movieservice.presentation.dto.request.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.RegisterMovieResponse;

import java.util.UUID;

public interface MovieUseCase {
    RegisterMovieResponse register(UUID creatorId, RegisterMovieRequest request);

    void updateVisibility(UUID creatorId, Long movieId, UpdateVisibilityRequest request);

    void updateDetail(UUID creatorId, Long movieId, UpdateDetailRequest request);
}
