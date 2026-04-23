package com.example.streamingservice.presentation;

import com.example.streamingservice.application.usecase.LifecycleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"dev","prod"})
@RestController
@RequestMapping("/internal/test/jobs")
@RequiredArgsConstructor
public class JobTestController {

	private final LifecycleUseCase lifecycle;

	@PostMapping("/lobby-open/{scheduleId}")
	public ResponseEntity<Void> lobbyOpen(@PathVariable long scheduleId) {
		lifecycle.onLobbyOpen(scheduleId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/starting-soon/{scheduleId}")
	public ResponseEntity<Void> startingSoon(@PathVariable long scheduleId) {
		lifecycle.onStartingSoon(scheduleId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/started/{scheduleId}")
	public ResponseEntity<Void> started(@PathVariable long scheduleId) {
		lifecycle.onStarted(scheduleId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/ending-soon/{scheduleId}")
	public ResponseEntity<Void> endingSoon(@PathVariable long scheduleId) {
		lifecycle.onEndingSoon(scheduleId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/ended/{scheduleId}")
	public ResponseEntity<Void> ended(@PathVariable long scheduleId) {
		lifecycle.onEnded(scheduleId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/force-exit/{scheduleId}")
	public ResponseEntity<Void> forceExit(@PathVariable long scheduleId) {
		lifecycle.onForceExit(scheduleId);
		return ResponseEntity.ok().build();
	}
}