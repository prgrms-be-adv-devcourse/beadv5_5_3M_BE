package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.domain.model.*;
import com.example.movieservice.domain.repository.*;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.presentation.dto.request.movie.RegisterMovieRequest;
import com.example.movieservice.presentation.dto.request.movie.UpdateDetailRequest;
import com.example.movieservice.presentation.dto.request.movie.UpdateVisibilityRequest;
import com.example.movieservice.presentation.dto.response.movie.*;
import com.example.movieservice.presentation.dto.response.review.ReviewSummaryResponse;
import com.example.movieservice.presentation.dto.response.schedule.ScheduleForUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        if(scheduleRepository.existsConfirmedScheduleByMovieId(movieId)){
            throw new GeneralException(ErrorStatus.MOVIE_ALREADY_SCHEDULED);
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
        // 편성이 확정된 게 있다면 삭제 불가
        if(scheduleRepository.existsConfirmedScheduleByMovieId(movieId)){
            throw new GeneralException(ErrorStatus.MOVIE_ALREADY_SCHEDULED);
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
    public DetailForUserResponse getDetailForUser(Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId).orElseThrow(()->new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));
        if(movie.getVisibility() == Movie.Visibility.PRIVATE){
            throw new GeneralException(ErrorStatus.MOVIE_NOT_PUBLIC);
        }

        List<Long> categoryIds = movie.getCategories().stream()
                .map(Category::getCategoryId)
                .toList();

        List<ScheduleForUserResponse> schedules = scheduleRepository.findUpcomingByMovieId(movieId, LocalDateTime.now())
                .stream()
                .map(s -> new ScheduleForUserResponse(s.getScheduleId(), s.getStartTime(), s.getRemainingSeats(), s.getStatus().name()))
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
                schedules,
                reviews
        );
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

    @Override
    public List<MovieCardResponse> getOnAirMovieList() {
        List<Movie> movies = scheduleRepository.findOnAirMovies();
        Map<UUID, String> nicknameMap = buildNicknameMap(movies.stream().map(Movie::getCreatorId).toList());

        return movies.stream()
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieCardResponse(movie.getMovieId(),
                            movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(),
                            Math.round(movie.getAverageRating() * 10) / 10.0f,
                            categoryIds);
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
                    return new ScheduledMovieResponse(movie.getMovieId(),
                            movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(),
                            schedule.getStartTime(),
                            categoryIds);
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
                    return new MovieCardResponse(movie.getMovieId(),
                            movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(),
                            Math.round(movie.getAverageRating() * 10) / 10.0f,
                            categoryIds);
                })
                .toList();
    }

    @Override
    public List<MovieCardResponse> getMovieListByGenre(Long categoryId) {
        if(!categoryRepository.existsById(categoryId)){
            throw new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND);
        }
        List<Movie> movies = movieRepository.findAllByCategoryId(categoryId);
        Map<UUID, String> nicknameMap = buildNicknameMap(movies.stream().map(Movie::getCreatorId).toList());

        return movies.stream()
                .map(movie -> {
                    List<Long> categoryIds = movie.getCategories().stream().map(Category::getCategoryId).toList();
                    return new MovieCardResponse(movie.getMovieId(),
                            movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(),
                            Math.round(movie.getAverageRating() * 10) / 10.0f,
                            categoryIds);
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
                    return new MovieCardResponse(movie.getMovieId(),
                            movie.getCreatorId(),
                            nicknameMap.getOrDefault(movie.getCreatorId(), "알 수 없음"),
                            movie.getTitle(),
                            Math.round(movie.getAverageRating() * 10) / 10.0f,
                            categoryIds);
                })
                .toList();
    }

    private Map<UUID, String> buildNicknameMap(List<UUID> creatorIds) {
        return creatorRepository.findAllByCreatorIdIn(creatorIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(Creator::getCreatorId, Creator::getNickname));
    }

}
