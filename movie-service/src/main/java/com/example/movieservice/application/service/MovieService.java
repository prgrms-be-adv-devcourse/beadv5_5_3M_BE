package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.domain.model.Category;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.CategoryRepository;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.request.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.RegisterMovieResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService implements MovieUseCase {

    private final MovieRepository movieRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public RegisterMovieResponse register(UUID creatorId, RegisterMovieRequest request) {
        if(movieRepository.countByCreatorId(creatorId)>=3){
            throw new GeneralException(ErrorStatus.MOVIE_REGISTRATION_LIMIT_EXCEEDED);
        }

        Movie movie = Movie.builder()
                .creatorId(creatorId)
                .title(request.title())
                .description(request.description())
                .visibility(Movie.Visibility.PRIVATE)
                .runningTime(request.runningTime())
                .baseCookie(request.baseCookie())
                .additionalCookie(request.additionalCookie())
                .averageRating(0.0F)
                .reviewCount(0)
                .build();

        if(request.categoryIds() != null){
            request.categoryIds().forEach(categoryId -> {
                Category category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND));
                movie.addCategory(category);
            });
        }

        return new RegisterMovieResponse(movieRepository.save(movie).getMovieId());
    }

    @Override
    @Transactional
    public void updateVisibility(UUID creatorId, Long movieId, UpdateVisibilityRequest request) {
        Movie movie = movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(!movie.getCreatorId().equals(creatorId)){
            throw new GeneralException(ErrorStatus.MOVIE_INVALID_CREATOR);
        }
        movie.updateVisibility(request.visibility());
    }

}
