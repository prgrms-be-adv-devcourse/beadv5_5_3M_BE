package com.example.aiservice.infrastructure.llm;

import com.example.aiservice.domain.model.CandidateMovie;
import com.example.aiservice.domain.model.UserPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiLlmRerankingClient implements LlmRerankingClient {

    private final RestClient restClient;
    private final RestClient immediateRestClient; // rerankImmediate 전용 — 5초 timeout
    private final ObjectMapper objectMapper;

    @Value("${openai.reranking-model}")
    private String rerankingModel;

    public OpenAiLlmRerankingClient(
            @Value("${openai.api-key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.immediateRestClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        this.objectMapper = objectMapper;
    }

    @Override
    public String buildBatchLine(String customId, List<CandidateMovie> candidates, UserPreference user, int topN) {
        Map<String, Object> body = Map.of(
                "model", rerankingModel,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt(topN)),
                        Map.of("role", "user", "content", buildUserPrompt(candidates, user, topN))
                )
        );
        Map<String, Object> line = Map.of(
                "custom_id", customId,
                "method", "POST",
                "url", "/v1/chat/completions",
                "body", body
        );
        return objectMapper.writeValueAsString(line);
    }

    @Override
    public String submitBatch(List<String> jsonlLines) {
        String content = String.join("\n", jsonlLines);

        // Step 1: JSONL 파일 업로드
        MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
        uploadBody.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "batch_input.jsonl";
            }
        });
        uploadBody.add("purpose", "batch");

        String fileResponse = restClient.post()
                .uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(uploadBody)
                .retrieve()
                .body(String.class);

        String inputFileId = objectMapper.readTree(fileResponse).path("id").asText();
        log.info("[LLM] 배치 파일 업로드 완료 - fileId: {}", inputFileId);

        // Step 2: Batch 생성 (24h completion_window)
        Map<String, Object> batchRequest = Map.of(
                "input_file_id", inputFileId,
                "endpoint", "/v1/chat/completions",
                "completion_window", "24h"
        );
        String batchResponse = restClient.post()
                .uri("/batches")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(batchRequest))
                .retrieve()
                .body(String.class);

        String batchId = objectMapper.readTree(batchResponse).path("id").asText();
        log.info("[LLM] 배치 생성 완료 - batchId: {}", batchId);
        return batchId;
    }

    @Override
    public boolean isBatchCompleted(String batchId) {
        String response = restClient.get()
                .uri("/batches/{batchId}", batchId)
                .retrieve()
                .body(String.class);

        String status = objectMapper.readTree(response).path("status").asText();
        log.info("[LLM] 배치 상태 조회 - batchId: {}, status: {}", batchId, status);

        if ("failed".equals(status) || "expired".equals(status) || "cancelled".equals(status)) {
            log.warn("[LLM] 배치 비정상 종료 - batchId: {}, status: {}", batchId, status);
        }
        return "completed".equals(status);
    }

    @Override
    public Map<String, List<Long>> getBatchResults(String batchId) {
        // 배치 상태에서 output_file_id 조회
        String batchResponse = restClient.get()
                .uri("/batches/{batchId}", batchId)
                .retrieve()
                .body(String.class);
        String outputFileId = objectMapper.readTree(batchResponse).path("output_file_id").asText();

        // 결과 파일 다운로드
        String resultContent = restClient.get()
                .uri("/files/{fileId}/content", outputFileId)
                .retrieve()
                .body(String.class);

        // JSONL 파싱
        Map<String, List<Long>> results = new HashMap<>();
        for (String line : resultContent.split("\n")) {
            if (line.isBlank()) continue;
            try {
                JsonNode lineNode = objectMapper.readTree(line);
                String customId = lineNode.path("custom_id").asText();

                JsonNode errorNode = lineNode.path("error");
                if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                    log.warn("[LLM] 배치 결과 오류 - customId: {}, error: {}", customId, errorNode);
                    continue;
                }

                int statusCode = lineNode.path("response").path("status_code").asInt();
                if (statusCode != 200) {
                    log.warn("[LLM] 배치 결과 비정상 응답 - customId: {}, statusCode: {}", customId, statusCode);
                    continue;
                }

                String content = lineNode
                        .path("response").path("body")
                        .path("choices").get(0)
                        .path("message").path("content").asText();

                List<Long> rankedIds = new ArrayList<>();
                for (JsonNode idNode : objectMapper.readTree(content).path("ranked_ids")) {
                    rankedIds.add(idNode.asLong());
                }
                results.put(customId, rankedIds);
            } catch (Exception e) {
                log.warn("[LLM] 결과 라인 파싱 실패: {}", line, e);
            }
        }
        log.info("[LLM] 배치 결과 파싱 완료 - 성공: {}명", results.size());
        return results;
    }

    @Override
    public List<Long> rerankImmediate(List<CandidateMovie> candidates, UserPreference user, int topN) {
        Map<String, Object> body = Map.of(
                "model", rerankingModel,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt(topN)),
                        Map.of("role", "user", "content", buildUserPrompt(candidates, user, topN))
                )
        );

        String response = immediateRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        String content = objectMapper.readTree(response)
                .path("choices").get(0)
                .path("message").path("content").asText();

        List<Long> rankedIds = new ArrayList<>();
        for (JsonNode idNode : objectMapper.readTree(content).path("ranked_ids")) {
            rankedIds.add(idNode.asLong());
        }
        log.info("[LLM] 즉시 재순위 완료 - 결과: {}개", rankedIds.size());
        return rankedIds;
    }

    // ===

    private String buildSystemPrompt(int topN) {
        return """
                You are a movie recommendation assistant.
                Given a list of candidate movies and a user profile, select and rank the best %d movies for this user.
                Return ONLY a JSON object in this exact format: {"ranked_ids": [id1, id2, ..., id%d]}
                All IDs must come from the candidate list. Do not add, modify, or omit any IDs outside the list.
                """.formatted(topN, topN);
    }

    private String buildUserPrompt(List<CandidateMovie> candidates, UserPreference user, int topN) {
        StringBuilder sb = new StringBuilder();
        sb.append("User profile:\n");
        sb.append("- Age group: ").append(user.getAgeGroup()).append("s\n");
        sb.append("- Gender: ").append(user.getGender().name()).append("\n");
        sb.append("- Exploration tendency: ").append(epsilonToLabel(user.getEpsilon())).append("\n\n");
        sb.append("Candidate movies (select best ").append(topN).append("):\n");

        for (CandidateMovie c : candidates) {
            String categories = String.join(", ", c.category());
            sb.append("[").append(c.movieId()).append("] ")
              .append("[").append(categories).append("] ")
              .append(c.summary()).append("\n");
        }
        return sb.toString();
    }

    private String epsilonToLabel(double epsilon) {
        if (epsilon >= 0.3) return "high";
        if (epsilon >= 0.15) return "medium";
        return "low";
    }
}
