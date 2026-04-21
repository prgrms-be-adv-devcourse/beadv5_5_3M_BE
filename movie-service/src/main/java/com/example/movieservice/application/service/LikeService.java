package com.example.movieservice.application.service;

import com.example.movieservice.application.event.EventPublisher;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.model.MovieLike;
import com.example.movieservice.domain.repository.MovieLikeRepository;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.infrastructure.kafka.dto.publish.MovieLikedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final MovieRepository movieRepository;
    private final MovieLikeRepository movieLikeRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void like(UUID userId, Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        try {
            movieLikeRepository.save(MovieLike.builder().userId(userId).movieId(movieId).build());
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("[Like] 중복 좋아요 시도 - userId: {}, movieId: {}", userId, movieId);
                throw new GeneralException(ErrorStatus.LIKE_ALREADY_EXISTS);
            }
            throw e;
        }
        movie.increaseLikeCount();
        eventPublisher.publish("movie.liked", movieId.toString(), new MovieLikedMessage(userId, movieId, "LIKED"));

        log.info("[Like] 좋아요 추가 - userId: {}, movieId: {}", userId, movieId);
    }

    @Transactional
    public void unlike(UUID userId, Long movieId) {
        MovieLike like = movieLikeRepository.findByUserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.LIKE_NOT_FOUND));

        Movie movie = movieRepository.findByMovieId(movieId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        movieLikeRepository.delete(like);
        movie.decreaseLikeCount();
        eventPublisher.publish("movie.liked", movieId.toString(), new MovieLikedMessage(userId, movieId, "UNLIKED"));

        log.info("[Like] 좋아요 취소 - userId: {}, movieId: {}", userId, movieId);
    }
}