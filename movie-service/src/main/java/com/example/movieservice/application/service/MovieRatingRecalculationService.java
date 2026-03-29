package com.example.movieservice.application.service;

import com.example.movieservice.domain.repository.MovieRatingStats;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRatingRecalculationService {

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;

    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
//    @Scheduled(cron = "0 * * * * *") // 테스트용 1분마다
    @Transactional
    public void recalculate() {
        log.info("[Batch] 영화 평점 보정 시작");

        // 1단계: 리뷰 있는 영화 재계산
        List<MovieRatingStats> statsList = reviewRepository.findAllRatingStats();
        Set<Long> reviewedMovieIds = statsList.stream()
                .map(MovieRatingStats::getMovieId)
                .collect(Collectors.toSet());

        for (MovieRatingStats stats : statsList) {
            movieRepository.findByMovieId(stats.getMovieId()).ifPresent(movie ->
                    movie.recalculateRating(
                            stats.getReviewCount().intValue(),
                            stats.getAverageRating().floatValue()
                    )
            );
        }

        // 2단계: 리뷰가 없는 영화 초기화 (리뷰가 모두 삭제된 케이스)
        List<Long> allMovieIds = movieRepository.findAllMovieIds();
        long resetCount = allMovieIds.stream()
                .filter(movieId -> !reviewedMovieIds.contains(movieId))
                .peek(movieId -> movieRepository.findByMovieId(movieId).ifPresent(movie ->
                        movie.recalculateRating(0, 0f)
                ))
                .count();

        log.info("[Batch] 영화 평점 보정 완료 - 재계산: {}건, 초기화: {}건", statsList.size(), resetCount);
    }
}
