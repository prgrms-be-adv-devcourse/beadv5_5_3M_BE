package com.example.userservice.infrastructure.persistence.like;

import com.example.userservice.domain.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    boolean existsByMovieIdAndUserId(Long movieId, UUID userId);

    void deleteByMovieIdAndUserId(Long movieId, UUID userId);
}
