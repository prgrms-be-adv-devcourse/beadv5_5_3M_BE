package com.example.streamingservice.presentation;

import com.example.streamingservice.application.dto.IssueSessionCommand;
import com.example.streamingservice.application.dto.SessionIssueResult;
import com.example.streamingservice.application.usecase.EnterStreamUseCase;
import com.example.streamingservice.presentation.dto.SessionIssueRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/streaming")
@RequiredArgsConstructor
public class StreamingSessionController {

	private final EnterStreamUseCase enterStream;

	@PostMapping("/sessions")
	public ResponseEntity<SessionIssueResult> issue(
			@RequestHeader("X-User-Id") UUID userId,
			@RequestBody @Valid SessionIssueRequest request) {
		SessionIssueResult result = enterStream.issue(
			new IssueSessionCommand(userId, request.scheduleId()));
		return ResponseEntity.ok(result);
	}
}