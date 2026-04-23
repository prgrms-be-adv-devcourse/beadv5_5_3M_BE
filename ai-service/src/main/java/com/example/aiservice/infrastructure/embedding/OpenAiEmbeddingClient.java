package com.example.aiservice.infrastructure.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.summary-model}")
    private String summaryModel;

    @Value("${openai.embedding-model}")
    private String embeddingModel;

    @Value("${openai.embedding-dimensions}")
    private int embeddingDimensions;

    public OpenAiEmbeddingClient(
            @Value("${openai.api-key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public EmbeddingResult embed(String description, String[] category) {
        // Step 1: LLM 1회 호출 → 영어 summary + 영어 카테고리 번역
        SummaryResponse summaryResponse = generateSummaryAndTranslateCategories(description, category);

        // Step 2: "[카테고리들] " + summary → 임베딩 입력
        String categoryPrefix = "[" + String.join(", ", summaryResponse.categoriesEn()) + "]";
        String embeddingInput = categoryPrefix + " " + summaryResponse.summary();

        // Step 3: text-embedding-3-small 호출 → vector(1536)
        float[] vector = fetchEmbedding(embeddingInput);

        log.debug("[EmbeddingClient] 임베딩 생성 완료 - summary: {}", summaryResponse.summary());
        return new EmbeddingResult(
                vector,
                summaryResponse.summary(),
                summaryResponse.categoriesEn()
        );
    }

    private SummaryResponse generateSummaryAndTranslateCategories(String description, String[] category) {
        String categoriesStr = String.join(", ", category);
        String prompt = """
                You are processing Korean movie data for an English-language recommendation system.

                Given the movie description and its categories, respond ONLY with a JSON object in this exact format:
                {
                  "summary": "<2-3 sentence English summary capturing the core story and themes>",
                  "categoriesEn": ["<English translation of each category>"]
                }

                Categories: %s
                Description: %s
                """.formatted(categoriesStr, description);

        Map<String, Object> requestBody = Map.of(
                "model", summaryModel,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        String responseBody = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        return objectMapper.readValue(content, SummaryResponse.class);
    }

    private float[] fetchEmbedding(String text) {
        Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", text,
                "dimensions", embeddingDimensions
        );

        String responseBody = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode embeddingNode = root.path("data").get(0).path("embedding");

        float[] vector = new float[embeddingDimensions];
        for (int i = 0; i < embeddingDimensions; i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    private record SummaryResponse(String summary, String[] categoriesEn) {}
}
