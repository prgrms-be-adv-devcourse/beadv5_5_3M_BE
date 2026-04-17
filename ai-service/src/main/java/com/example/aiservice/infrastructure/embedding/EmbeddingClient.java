package com.example.aiservice.infrastructure.embedding;

public interface EmbeddingClient {

    /**
     * 영화 description과 원본 카테고리를 받아 임베딩 결과를 반환한다.
     * <p>
     * 내부 처리 순서:
     * 1. LLM 1회 호출 → 영어 summary 생성 + 카테고리 영어 번역
     * 2. "[카테고리들] " + summary → text-embedding-3-small → vector(1536)
     *
     * @param description 한국어 영화 설명
     * @param category    원본 카테고리 배열 (한국어/영어 혼용 가능)
     * @return summary, categoriesEn, embedding 포함 결과
     */
    EmbeddingResult embed(String description, String[] category);
}
