package com.example.userservice.infrastructure.persistence.like;

import com.example.userservice.domain.model.Like;
import com.example.userservice.domain.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryAdapter implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public void save(Like like) {
        likeJpaRepository.save(like);
    }

    @Override
    public boolean existsByMovieIdAndUserId(Long movieId, UUID userId) {
        return likeJpaRepository.existsByMovieIdAndUserId(movieId, userId);
    }

    @Override
    public void deleteByMovieIdAndUserId(Long movieId, UUID userId) {
        likeJpaRepository.deleteByMovieIdAndUserId(movieId, userId);
    }

    @Override
    public void deleteByMovieId(Long movieId) {
        likeJpaRepository.deleteByMovieId(movieId);
    }
}
