package com.example.movieservice.application.service;

import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.MovieRatingStats;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieBatchService {

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void recalculateMovieRatings() {
        log.info("[Batch] 영화 평점 보정 시작");

        List<MovieRatingStats> statsList = reviewRepository.findAllRatingStats();
        Set<Long> reviewedMovieIds = statsList.stream()
                .map(MovieRatingStats::getMovieId)
                .collect(Collectors.toSet());

        Map<Long, Movie> reviewedMovieMap = movieRepository.findAllByMovieIds(reviewedMovieIds).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity(), (a, b) -> a));

        for (MovieRatingStats stats : statsList) {
            Movie movie = reviewedMovieMap.get(stats.getMovieId());
            if (movie != null) {
                movie.recalculateRating(
                        stats.getReviewCount().intValue(),
                        stats.getAverageRating().floatValue()
                );
            }
        }

        List<Long> allMovieIds = movieRepository.findAllMovieIds();
        List<Long> resetTargetIds = allMovieIds.stream()
                .filter(movieId -> !reviewedMovieIds.contains(movieId))
                .toList();

        Map<Long, Movie> resetMovieMap = movieRepository.findAllByMovieIds(resetTargetIds).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity(), (a, b) -> a));

        long resetCount = resetTargetIds.stream()
                .peek(movieId -> {
                    Movie movie = resetMovieMap.get(movieId);
                    if (movie != null) movie.recalculateRating(0, 0f);
                })
                .count();

        log.info("[Batch] 영화 평점 보정 완료 - 재계산: {}건, 초기화: {}건", statsList.size(), resetCount);
    }
}