package com.example.aiservice.presentation.controller;

import com.example.aiservice.application.usecase.RecommendationUseCase;
import com.example.aiservice.presentation.dto.request.ClickRequest;
import com.example.aiservice.presentation.dto.response.RecommendationItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationUseCase recommendationUseCase;

    @GetMapping
    public ResponseEntity<List<RecommendationItemResponse>> getRecommendations(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(recommendationUseCase.getRecommendations(userId));
    }

    @PatchMapping("/click")
    public ResponseEntity<Void> click(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ClickRequest request
    ) {
        recommendationUseCase.click(userId, request.logId());
        return ResponseEntity.ok().build();
    }
}
