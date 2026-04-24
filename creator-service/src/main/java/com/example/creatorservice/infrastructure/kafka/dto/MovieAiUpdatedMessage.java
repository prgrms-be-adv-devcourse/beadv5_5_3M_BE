package com.example.creatorservice.infrastructure.kafka.dto;

import java.util.List;

// topic: movie.ai.updated
// producer: creator-service (영화 수정 시)
// consumer: ai-service
// 규칙:
//   - changedFields: 변경된 필드명 목록 ("category" / "description" / "visibility")
//   - 변경되지 않은 필드: null
//   - changedFields에 "category" 또는 "description"이 포함되면 description + category 모두 포함
public record MovieAiUpdatedMessage(
        Long movieId,
        List<String> changedFields,
        String description,
        String[] category,
        String visibility
) {}