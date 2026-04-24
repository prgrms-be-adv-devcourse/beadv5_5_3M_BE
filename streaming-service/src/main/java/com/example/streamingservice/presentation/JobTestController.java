package com.example.streamingservice.presentation;

import com.example.streamingservice.application.usecase.LifecycleUseCase;
import com.example.streamingservice.domain.Schedule;
import com.example.streamingservice.domain.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;

@Profile({"dev","prod"})
@RestController
@RequestMapping("/internal/test/jobs")
@RequiredArgsConstructor
public class JobTestController {

	private final LifecycleUseCase lifecycle;
	private final ScheduleRepository scheduleRepository;

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
		var schedule = scheduleRepository.findById(scheduleId)
				.orElseThrow(() -> new RuntimeException("schedule not found"));
		schedule.setStartTime(Instant.now());
		scheduleRepository.save(schedule);
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
		var schedule = scheduleRepository.findById(scheduleId)
				.orElseThrow(() -> new RuntimeException("schedule not found"));
		schedule.setEndTime(Instant.now());
		scheduleRepository.save(schedule);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/force-exit/{scheduleId}")
	public ResponseEntity<Void> forceExit(@PathVariable long scheduleId) {
		lifecycle.onForceExit(scheduleId);
		return ResponseEntity.ok().build();
	}
}