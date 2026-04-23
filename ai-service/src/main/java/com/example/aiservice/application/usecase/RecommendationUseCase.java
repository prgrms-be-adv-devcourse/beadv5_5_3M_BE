package com.example.aiservice.application.usecase;

import com.example.aiservice.presentation.dto.response.RecommendationItemResponse;

import java.util.List;
import java.util.UUID;

public interface RecommendationUseCase {

    List<RecommendationItemResponse> getRecommendations(UUID userId);

    void click(UUID userId, Long logId);
}
