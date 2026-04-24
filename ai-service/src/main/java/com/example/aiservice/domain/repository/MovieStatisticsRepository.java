package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.MovieStatistics;
import com.example.aiservice.domain.model.enums.Gender;

import java.util.List;

public interface MovieStatisticsRepository {

    void saveAll(List<MovieStatistics> statistics);

    void deleteByMovieId(Long movieId);

    void incrementWatchCount(Long movieId, int ageGroup, Gender gender);

    List<Long> findDemographicCandidates(int ageGroup, Gender gender, int limit);

    List<Long> findLowExposureCandidates(int limit);
}
