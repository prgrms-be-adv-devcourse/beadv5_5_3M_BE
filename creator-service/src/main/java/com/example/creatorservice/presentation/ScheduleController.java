package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.ScheduleManageUseCase;
import com.example.creatorservice.presentation.dto.req.ConfirmScheduleRequest;
import com.example.creatorservice.presentation.dto.req.RegisterScheduleRequest;
import com.example.creatorservice.presentation.dto.res.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Schedule", description = "상영 일정 관리 API (크리에이터 전용)")
@RestController
@RequestMapping("/api/creators/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleManageUseCase scheduleManageUseCase;

    @Operation(summary = "일정 등록", description = "하나의 영화에 대해 여러 상영 일정을 등록합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<List<Long>> register(
            @RequestHeader("X-Creator-Id") String creatorId,
            @RequestBody @Valid RegisterScheduleRequest request) {
        List<Long> ids = scheduleManageUseCase.register(UUID.fromString(creatorId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ids);
    }

    @Operation(summary = "일정 확정", description = "등록된 일정들을 확정합니다. Kafka 이벤트를 발행합니다.")
    @PatchMapping("/confirm")
    public ResponseEntity<Void> confirm(
            @RequestHeader("X-Creator-Id") String creatorId,
            @RequestBody @Valid ConfirmScheduleRequest request) {
        scheduleManageUseCase.confirm(UUID.fromString(creatorId), request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "미확정 일정 삭제", description = "아직 확정되지 않은 일정을 삭제합니다.")
    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Creator-Id") String creatorId,
            @PathVariable Long scheduleId) {
        scheduleManageUseCase.delete(UUID.fromString(creatorId), scheduleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "특정 날짜 미확정 일정 조회", description = "특정 날짜의 미확정 일정 목록을 조회합니다.")
    @GetMapping("/draft")
    public ResponseEntity<List<ScheduleResponse>> getDraftByDate(
            @RequestHeader("X-Creator-Id") String creatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleManageUseCase.getDraftByDate(UUID.fromString(creatorId), date));
    }

    @Operation(summary = "특정 날짜 확정 일정 조회", description = "특정 날짜의 확정된 상영 일정 목록을 조회합니다.")
    @GetMapping("/confirmed")
    public ResponseEntity<List<ScheduleResponse>> getConfirmedByDate(
            @RequestHeader("X-Creator-Id") String creatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleManageUseCase.getConfirmedByDate(UUID.fromString(creatorId), date));
    }
}