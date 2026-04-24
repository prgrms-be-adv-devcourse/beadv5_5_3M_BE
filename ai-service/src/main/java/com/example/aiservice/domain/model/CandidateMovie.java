package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.ExplorationSource;

public record CandidateMovie(
        Long movieId,
        String[] category,
        String summary,
        boolean isExploration,
        ExplorationSource explorationSource,
        float similarity
) {
    public static CandidateMovie exploitation(Long movieId, String[] category, String summary, float similarity) {
        return new CandidateMovie(movieId, category, summary, false, null, similarity);
    }

    public static CandidateMovie exploration(Long movieId, String[] category, String summary, ExplorationSource source) {
        return new CandidateMovie(movieId, category, summary, true, source, 0.0f);
    }
}
