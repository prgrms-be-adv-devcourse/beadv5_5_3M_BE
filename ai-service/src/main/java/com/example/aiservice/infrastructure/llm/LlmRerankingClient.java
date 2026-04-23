package com.example.aiservice.infrastructure.llm;

import com.example.aiservice.domain.model.CandidateMovie;
import com.example.aiservice.domain.model.UserPreference;

import java.util.List;
import java.util.Map;

public interface LlmRerankingClient {

    /**
     * OpenAI Batch API 제출용 JSONL 라인 1개를 생성한다.
     *
     * @param customId  결과 수령 시 유저 식별에 사용하는 키 (userId.toString())
     * @param candidates 후보 영화 목록
     * @param user       유저 프로파일 (나이/성별/epsilon 프롬프트 context용)
     * @param topN       최종 반환 개수
     * @return JSONL 한 줄 (JSON string)
     */
    String buildBatchLine(String customId, List<CandidateMovie> candidates, UserPreference user, int topN);

    /**
     * JSONL 라인 목록을 OpenAI Batch API에 제출한다.
     *
     * @return OpenAI batch_id
     */
    String submitBatch(List<String> jsonlLines);

    /**
     * Batch API 완료 여부를 확인한다. failed/expired/cancelled 상태도 false를 반환한다.
     */
    boolean isBatchCompleted(String batchId);

    /**
     * 완료된 배치 결과를 수령한다.
     *
     * @return customId(userId) → 순위별 movie_id 목록
     */
    Map<String, List<Long>> getBatchResults(String batchId);

    /**
     * 단일 유저에 대해 Chat Completions API를 동기 호출해 즉시 재순위를 반환한다.
     * Batch API와 달리 즉시 응답이 필요한 trigger 재계산에 사용.
     * 실패 시 호출자가 벡터 fallback으로 처리한다.
     */
    List<Long> rerankImmediate(List<CandidateMovie> candidates, UserPreference user, int topN);
}
