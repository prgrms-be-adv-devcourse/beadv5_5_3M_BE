package com.example.aiservice.domain.model;

import com.example.aiservice.domain.model.enums.ExplorationSource;

public record CandidateInfo(boolean isExploration, ExplorationSource explorationSource) {}
