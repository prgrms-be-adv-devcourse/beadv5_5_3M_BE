package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.MovieStatistics;

import java.util.List;

public interface MovieStatisticsRepository {

    void saveAll(List<MovieStatistics> statistics);

    void deleteByMovieId(Long movieId);
}
