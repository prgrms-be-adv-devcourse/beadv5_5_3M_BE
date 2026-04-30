package com.example.ticketservice.presentation.controller;

import com.example.ticketservice.application.dto.response.MovieScheduleResponse;
import com.example.ticketservice.application.dto.response.TicketableScheduleResponse;
import com.example.ticketservice.application.usecase.ScheduleQueryUseCase;
import com.example.ticketservice.common.model.PageResult;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Schedule", description = "스케줄(회차) 조회 API")
@RestController
@RequestMapping("/api/tickets/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleQueryUseCase scheduleQueryUseCase;

    @Operation(
            summary = "티켓팅 진입 가능한 스케줄 목록",
            description = "사용자 관점 3단계(장바구니/가결제기간/티켓팅기간)의 스케줄을 페이지네이션해서 회차 단위로 반환합니다. " +
                    "TICKETING 단계 회차의 잔여 좌석은 Redis stock 카운터에서 함께 내려옵니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "허용되지 않은 status (STREAMING/FINISH 등)")
    })
    @GetMapping("/open")
    public ResponseEntity<PageResult<TicketableScheduleResponse>> listOpenSchedules(
            @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "필터링할 status (CART, IN_PROGRESSING, TICKETING). 미지정 시 3개 모두 포함.")
            @RequestParam(required = false) Set<ScheduleStatus> status,
            @PageableDefault(size = 20, sort = "ticketingTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(scheduleQueryUseCase.listOpenSchedules(status, pageable));
    }

    @Operation(
            summary = "영화별 진행 가능 회차 목록",
            description = "특정 영화의 CART/IN_PROGRESSING/TICKETING/STREAMING 회차를 startTime ASC로 반환합니다. " +
                    "TICKETING 회차의 잔여 좌석은 Redis stock 카운터에서 함께 내려옵니다. FINISH는 제외."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (회차 0개여도 200 + 빈 배열)")
    })
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<MovieScheduleResponse>> listSchedulesByMovieId(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long movieId
    ) {
        return ResponseEntity.ok(scheduleQueryUseCase.listSchedulesByMovieId(movieId));
    }
}