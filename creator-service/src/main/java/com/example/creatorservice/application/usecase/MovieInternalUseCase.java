package com.example.creatorservice.application.usecase;

import com.example.creatorservice.presentation.dto.MovieLocationResponse;

public interface MovieInternalUseCase {
    MovieLocationResponse getLocation(Long movieId);
}