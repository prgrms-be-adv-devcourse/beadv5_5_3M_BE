package com.example.movieservice.application.service;

import com.example.movieservice.domain.model.Schedule;
import com.example.movieservice.domain.repository.MovieRatingStats;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.domain.repository.ReviewRepository;
import com.example.movieservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieBatchService {

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;
    private final ScheduleRepository scheduleRepository;

//    @Scheduled(cron = "0 * * * * *") // 테스트용 1분마다
    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
    @Transactional
    public void recalculateMovieRatings() {
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

    @Scheduled(cron = "0 * * * * *") // 매 1분마다
    @Transactional
    public void updateScheduleStatuses() {
        LocalDateTime now = LocalDateTime.now();

        // SCHEDULED → WAITING: 시작 10분 전 진입
        List<Schedule> toWaiting = scheduleRepository.findScheduledToWaiting(now, now.plusMinutes(10));
        toWaiting.forEach(Schedule::waiting);

        // SCHEDULED/WAITING → ON_AIR: 시작 시간 도달 (배치 지연으로 WAITING을 건너뛴 경우 포함)
        List<Schedule> toOnAir = scheduleRepository.findToOnAir(now);
        toOnAir.forEach(Schedule::start);

        // ON_AIR → COMPLETED: 종료 후 10분 경과
        List<Schedule> toCompleted = scheduleRepository.findOnAirToCompleted(now.minusMinutes(10));
        toCompleted.forEach(Schedule::complete);

        log.info("[Batch] 스케줄 상태 업데이트 - WAITING: {}건, ON_AIR: {}건, COMPLETED: {}건",
                toWaiting.size(), toOnAir.size(), toCompleted.size());
    }
}
