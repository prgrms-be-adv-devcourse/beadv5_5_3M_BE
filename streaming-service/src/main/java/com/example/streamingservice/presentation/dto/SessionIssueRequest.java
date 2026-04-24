package com.example.streamingservice.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record SessionIssueRequest(@NotNull Long scheduleId) {
}