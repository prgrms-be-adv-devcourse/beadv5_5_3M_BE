package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieStatistics;
import com.example.aiservice.domain.model.enums.Gender;
import com.example.aiservice.domain.repository.MovieStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MovieStatisticsRepositoryImpl implements MovieStatisticsRepository {

    private final MovieStatisticsJpaRepository movieStatisticsJpaRepository;

    @Override
    public void saveAll(List<MovieStatistics> statistics) {
        movieStatisticsJpaRepository.saveAll(statistics);
    }

    @Override
    public void deleteByMovieId(Long movieId) {
        movieStatisticsJpaRepository.deleteByMovieId(movieId);
    }

    @Override
    public void incrementWatchCount(Long movieId, int ageGroup, Gender gender) {
        movieStatisticsJpaRepository.incrementWatchCount(movieId, ageGroup, gender);
    }

}
