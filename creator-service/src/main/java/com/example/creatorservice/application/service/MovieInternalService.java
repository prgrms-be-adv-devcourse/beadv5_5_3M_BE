package com.example.creatorservice.application.service;

import com.example.creatorservice.application.exception.MovieException;
import com.example.creatorservice.application.usecase.MovieInternalUseCase;
import com.example.creatorservice.domain.model.Movie;
import com.example.creatorservice.domain.repository.MovieRepository;
import com.example.creatorservice.presentation.dto.MovieLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieInternalService implements MovieInternalUseCase {

    private final MovieRepository movieRepository;

    @Override
    public MovieLocationResponse getLocation(Long movieId) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(MovieException::notFound);
        return MovieLocationResponse.from(movie);
    }
}