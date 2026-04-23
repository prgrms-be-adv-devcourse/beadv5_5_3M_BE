package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Like", description = "영화 좋아요 API")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "좋아요 추가")
    @PostMapping("/{movieId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> like(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long movieId) {
        likeService.like(UUID.fromString(userId), movieId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "좋아요 취소")
    @DeleteMapping("/{movieId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> unlike(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long movieId) {
        likeService.unlike(UUID.fromString(userId), movieId);
        return ResponseEntity.noContent().build();
    }
}