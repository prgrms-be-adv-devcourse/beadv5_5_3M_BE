package com.example.aiservice.infrastructure.embedding;

public record EmbeddingResult(
        float[] embedding,
        String summary,
        String[] categoriesEn
) {
}
