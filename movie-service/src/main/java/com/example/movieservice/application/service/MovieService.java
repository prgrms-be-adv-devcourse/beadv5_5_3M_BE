package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.domain.model.*;
import com.example.movieservice.domain.repository.*;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.response.movie.*;
import com.example.movieservice.presentation.dto.response.review.ReviewSummaryResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService implements MovieUseCase {

    private final MovieRepository movieRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReviewRepository reviewRepository;
    private final CreatorRepository creatorRepository;

    @Override
    public DetailForUserResponse getDetailForUser(Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if (movie.getVisibility() == Movie.Visibility.PRIVATE) {
            throw new GeneralException(ErrorStatus.MOVIE_NOT_PUBLIC);
        }

        List<Long> categoryIds = movie.getCategories().stream()
                .map(Category::getCategoryId)
                .toList();

        List<ScheduleForUserResponse> schedules = scheduleRepository.findUpcomingByMovieId(movieId, LocalDateTime.now())
                .stream()
                .map(s -> new ScheduleForUserResponse(s.getScheduleId(), s.getTicketingTime(), s.getStartTime(), s.getRemainingSeats(), s.getStatus().name()))
                .toList();

        List<ReviewSummaryResponse> reviews = reviewRepository.findTop5ByMovieId(movieId)
                .stream()
                .map(r -> new ReviewSummaryResponse(r.getReviewId(), r.getNickname(), r.getRating(), r.getComment(), r.getUpdatedAt(), r.getStatus().name()))
                .toList();

        String nickname = creatorRepository.findById(movie.getCreatorId())
                .map(Creator::getNickname)
                .orElse("알 수 없음");

        return new DetailForUserResponse(
                movie.getCreatorId(),
                nickname,
                movie.getTitle(),
                movie.getDescription(),
                categoryIds,
                movie.getRunningTime(),
                Math.round(movie.getAverageRating() * 10) / 10.0f,
                movie.getBaseCookie() + movie.getAdditionalCookie(),
                movie.getImageUrl(),
                movie.getLikeCount(),
                schedules,
                reviews
        );
    }

    @Override
    public List<MovieByCreatorResponse> getMovieListByCreator(UUID creatorId) {
        return movieRepository.findMoviesByCreatorId(creatorId).stream()
                .filter(movie -> movie.getVisibility() == Movie.Visibility.PUBLIC)
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieByCreatorResponse(movie.getMovieId(), movie.getTitle(), Math.round(movie.getAverageRating() * 10) / 10.0f, categoryIds, movie.getImageUrl());
                })
                .toList();
    }

    @Override
    public List<MovieCardResponse> getOnAirMovieList() {
        List<Movie> movies = scheduleRepository.findOnAirMovies();
        Map<UUID, String> nicknameMap = buildNicknameMap(movies.stream().map(Movie::getCreatorId).toList());

        return movies.stream()
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieCardResponse(movie.getMovieId(), movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(), movie.getImageUrl(), Math.round(movie.getAverageRating() * 10) / 10.0f, categoryIds, null, null);
                })
                .toList();
    }

    @Override
    public List<ScheduledMovieResponse> getScheduledMovieList() {
        List<Schedule> schedules = scheduleRepository.findScheduledMovies();
        Map<UUID, String> nicknameMap = buildNicknameMap(schedules.stream().map(s -> s.getMovie().getCreatorId()).toList());

        return schedules.stream()
                .map(schedule -> {
                    Movie movie = schedule.getMovie();
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new ScheduledMovieResponse(movie.getMovieId(), movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(), schedule.getStartTime(), categoryIds, movie.getImageUrl());
                })
                .toList();
    }

    @Override
    public List<MovieCardResponse> getPublicMovieList() {
        List<Movie> movies = movieRepository.findAllPublic();
        Map<UUID, String> nicknameMap = buildNicknameMap(movies.stream().map(Movie::getCreatorId).toList());

        return movies.stream()
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieCardResponse(movie.getMovieId(), movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(), movie.getImageUrl(), Math.round(movie.getAverageRating() * 10) / 10.0f, categoryIds, null, null);
                })
                .toList();
    }

    @Override
    public List<MovieCardResponse> getMovieListByGenre(Long categoryId) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND);
        }
        List<Movie> movies = movieRepository.findAllByCategoryId(categoryId);
        Map<UUID, String> nicknameMap = buildNicknameMap(movies.stream().map(Movie::getCreatorId).toList());

        return movies.stream()
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieCardResponse(movie.getMovieId(), movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(), movie.getImageUrl(), Math.round(movie.getAverageRating() * 10) / 10.0f, categoryIds, null, null);
                })
                .toList();
    }

    @Override
    public List<MovieCardResponse> searchMoviesByTitle(String title) {
        List<Movie> movies = movieRepository.searchByTitle(title);
        Map<UUID, String> nicknameMap = buildNicknameMap(movies.stream().map(Movie::getCreatorId).toList());

        return movies.stream()
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieCardResponse(movie.getMovieId(), movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(), movie.getImageUrl(), Math.round(movie.getAverageRating() * 10) / 10.0f, categoryIds, null, null);
                })
                .toList();
    }

    private Map<UUID, String> buildNicknameMap(List<UUID> creatorIds) {
        return creatorRepository.findAllByCreatorIdIn(creatorIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(Creator::getCreatorId, Creator::getNickname));
    }
}