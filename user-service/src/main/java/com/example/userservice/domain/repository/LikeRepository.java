package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.Like;

import java.util.UUID;

public interface LikeRepository {

    void save(Like like);

    boolean existsByMovieIdAndUserId(Long movieId, UUID userId);

    void deleteByMovieIdAndUserId(Long movieId, UUID userId);
}
