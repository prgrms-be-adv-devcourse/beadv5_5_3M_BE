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

    @Override
    public List<Long> findDemographicCandidates(int ageGroup, Gender gender, int limit) {
        // native query는 enum 자동 변환 불가 → gender.name()으로 전달
        return movieStatisticsJpaRepository.findDemographicCandidates(ageGroup, gender.name(), limit);
    }

    @Override
    public List<Long> findLowExposureCandidates(int limit) {
        return movieStatisticsJpaRepository.findLowExposureCandidates(limit);
    }

}
