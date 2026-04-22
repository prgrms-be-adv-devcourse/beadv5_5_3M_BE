package com.example.movieservice.infrastructure.kafka.dto.publish;

import java.util.UUID;

// topic: movie.liked
// receivers:
//   - user-service (groupId: user-service) : 좋아요 목록 동기화
//   - ai-service   (groupId: ai-service)   : 추천 모델 학습 데이터
public record MovieLikedMessage(
        UUID userId,
        Long movieId,
        String action  // "LIKED" | "UNLIKED"
) {}