package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.domain.model.Category;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.CategoryRepository;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ScheduleRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.request.movie.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.movie.UpdateDetailRequest;
import com.example.movieservice.presentation.dto.request.movie.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.movie.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService implements MovieUseCase {

    private final MovieRepository movieRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduleRepository scheduleRepository;

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
        movie.updateVisibility(Movie.Visibility.valueOf(request.visibility().name()));
    }

    @Override
    @Transactional
    public void updateDetail(UUID creatorId, Long movieId, UpdateDetailRequest request) {
        Movie movie = movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(!movie.getCreatorId().equals(creatorId)){
            throw new GeneralException(ErrorStatus.MOVIE_INVALID_CREATOR);
        }

        // 편성이 확정된 게 있다면 수정 불가
        if(scheduleRepository.existsConfirmedScheduleByMovieId(movieId)){
            throw new GeneralException(ErrorStatus.MOVIE_ALREADY_SCHEDULED);
        }
        movie.updateDetail(request.title(), request.description(), request.additionalCookie());

        movie.getCategories().clear();

        if(request.categoryIds() != null){
            request.categoryIds().forEach(categoryId -> {
                Category category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND));
                movie.addCategory(category);
            });
        }

    }

    @Override
    @Transactional
    public void delete(UUID creatorId, Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(!movie.getCreatorId().equals(creatorId)){
            throw new GeneralException(ErrorStatus.MOVIE_INVALID_CREATOR);
        }
        movie.getCategories().clear();
        movieRepository.delete(movie);
    }

    @Override
    public DetailForCreatorResponse getDetailForCreator(UUID creatorId, Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(!movie.getCreatorId().equals(creatorId)){
            throw new GeneralException(ErrorStatus.MOVIE_INVALID_CREATOR);
        }
        List<Long> categoryIds = movie.getCategories().stream()
                .map(Category::getCategoryId)
                .toList();
        return new DetailForCreatorResponse(movie.getTitle(), movie.getDescription(), categoryIds, movie.getBaseCookie(), movie.getAdditionalCookie());
    }

    @Override
    public DetailForUserResponse getDetailForUser(UUID userId, Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(movie.getVisibility() == Movie.Visibility.PRIVATE){
            throw new GeneralException(ErrorStatus.MOVIE_NOT_PUBLIC);
        }
        List<Long> categoryIds = movie.getCategories().stream()
                .map(Category::getCategoryId)
                .toList();
        return new DetailForUserResponse(movie.getCreatorId(), movie.getTitle(), movie.getDescription(), categoryIds, movie.getRunningTime(), Math.round(movie.getAverageRating() * 10) / 10.0f  , movie.getBaseCookie()+movie.getAdditionalCookie());
    }

    @Override
    public List<MovieByCreatorResponse> getMovieListByCreator(UUID creatorId) {
        List<Movie> movies = movieRepository.findMoviesByCreatorId(creatorId);

        return movies.stream()
                .filter(movie ->
                    movie.getVisibility()== Movie.Visibility.PUBLIC
                )
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieByCreatorResponse(movie.getMovieId(), movie.getTitle(), Math.round(movie.getAverageRating() * 10) / 10.0f , categoryIds);
                })
                .toList();
    }

    @Override
    public List<MovieForCreatorResponse> getMovieListForCreator(UUID creatorId) {
        List<Movie> movies = movieRepository.findMoviesByCreatorId(creatorId);
        return movies.stream()
                .map(movie -> {
                    return new MovieForCreatorResponse(movie.getMovieId(), movie.getTitle(), movie.getVisibility().name());
                })
                .toList();
    }

    @Override
    public List<MovieForScheduleResponse> getPublicMovieListForSchedule(UUID creatorId) {
        List<Movie> movies = movieRepository.findMoviesByCreatorId(creatorId);
        return movies.stream()
                .filter(movie -> movie.getVisibility()== Movie.Visibility.PUBLIC)
                .map(movie -> {
                    return new MovieForScheduleResponse(movie.getMovieId(), movie.getTitle(), movie.getRunningTime());
                }).toList();
    }

}
