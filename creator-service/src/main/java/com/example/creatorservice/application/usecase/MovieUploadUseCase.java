package com.example.creatorservice.application.usecase;

import com.example.creatorservice.presentation.dto.req.RegisterMovieRequest;
import com.example.creatorservice.presentation.dto.req.UpdateMovieDetailRequest;
import com.example.creatorservice.presentation.dto.req.UpdateVisibilityRequest;
import com.example.creatorservice.presentation.dto.res.MovieDetailResponse;
import com.example.creatorservice.presentation.dto.res.MovieListItemResponse;
import com.example.creatorservice.presentation.dto.res.SchedulableMovieResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MovieUploadUseCase {

    Long register(UUID creatorId, RegisterMovieRequest request, MultipartFile image, MultipartFile video);

    void updateVisibility(UUID creatorId, Long movieId, UpdateVisibilityRequest request);

    void updateDetail(UUID creatorId, Long movieId, UpdateMovieDetailRequest request);

    void delete(UUID creatorId, Long movieId);

    MovieDetailResponse getDetail(UUID creatorId, Long movieId);

    List<MovieListItemResponse> getMyMovies(UUID creatorId);

    List<SchedulableMovieResponse> getSchedulableMovies(UUID creatorId);
}