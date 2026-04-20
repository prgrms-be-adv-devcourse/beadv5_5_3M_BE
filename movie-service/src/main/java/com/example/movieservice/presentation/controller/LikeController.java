package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.event.EventPublisher;
import com.example.movieservice.application.service.LikeService;
import com.example.movieservice.infrastructure.kafka.dto.publish.MovieLikedMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Like", description = "영화 좋아요 API")
@Slf4j
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final EventPublisher eventPublisher;

    @Operation(summary = "좋아요 추가")
    @PostMapping("/{movieId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> like(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long movieId) {
        UUID userUuid = UUID.fromString(userId);

        likeService.like(userUuid, movieId);

        // TODO: Outbox 패턴 적용 필요 — 현재는 DB 커밋 후 Kafka 발행 실패 시 이벤트 유실 가능
        try {
            eventPublisher.publish("movie.liked", movieId.toString(),
                    new MovieLikedMessage(userUuid, movieId, "LIKED"));
        } catch (Exception e) {
            log.error("[Kafka] movie.liked 발행 실패 - userId: {}, movieId: {}", userUuid, movieId, e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "좋아요 취소")
    @DeleteMapping("/{movieId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> unlike(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long movieId) {
        UUID userUuid = UUID.fromString(userId);

        likeService.unlike(userUuid, movieId);

        // TODO: Outbox 패턴 적용 필요 — 현재는 DB 커밋 후 Kafka 발행 실패 시 이벤트 유실 가능
        try {
            eventPublisher.publish("movie.liked", movieId.toString(),
                    new MovieLikedMessage(userUuid, movieId, "UNLIKED"));
        } catch (Exception e) {
            log.error("[Kafka] movie.liked 발행 실패 - userId: {}, movieId: {}", userUuid, movieId, e);
        }

        return ResponseEntity.noContent().build();
    }
}