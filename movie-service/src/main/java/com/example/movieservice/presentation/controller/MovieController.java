package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.MovieSearchUseCase;
import com.example.movieservice.application.usecase.MovieUseCase;
import com.example.movieservice.presentation.dto.response.movie.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Movie", description = "영화 관련 API")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieUseCase movieUseCase;

    /**
     * ES 활성화 시에만 빈이 등록되므로 nullable.
     * movie.elasticsearch.enabled=false 이면 null → JPA 분기로만 동작.
     */
    @Autowired(required = false)
    private MovieSearchUseCase movieSearchUseCase;

    @Value("${movie.elasticsearch.enabled:false}")
    private boolean esEnabled;

    @Operation(summary = "영화 상세 조회 (사용자)", description = "사용자가 공개된 영화의 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "영화를 찾을 수 없음 또는 비공개 영화")
    })
    @GetMapping("/{movieId}/detail")
    public ResponseEntity<DetailForUserResponse> detailForUser(
            @Parameter(description = "조회할 영화 ID", required = true)
            @PathVariable Long movieId) {
        return ResponseEntity.ok(movieUseCase.getDetailForUser(movieId));
    }

    @Operation(summary = "크리에이터 공개 영화 목록 조회", description = "특정 크리에이터의 공개된 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/list")
    public ResponseEntity<List<MovieByCreatorResponse>> getMovieListByCreator(
            @Parameter(description = "조회할 크리에이터 ID", required = true)
            @RequestParam("creatorId") UUID creatorId) {
        return ResponseEntity.ok(movieUseCase.getMovieListByCreator(creatorId));
    }

    @Operation(summary = "현재 상영 중인 영화 목록 조회", description = "현재 ON_AIR 상태인 스케줄의 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/on-air")
    public ResponseEntity<List<MovieCardResponse>> getOnAirMovieList() {
        return ResponseEntity.ok(movieUseCase.getOnAirMovieList());
    }

    @Operation(summary = "상영 예정 영화 목록 조회", description = "SCHEDULED 상태인 스케줄 중 영화별 가장 빠른 상영 일정을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/scheduled")
    public ResponseEntity<List<ScheduledMovieResponse>> getScheduledMovieList() {
        return ResponseEntity.ok(movieUseCase.getScheduledMovieList());
    }

    @Operation(summary = "전체 공개 영화 목록 조회", description = "공개(PUBLIC) 상태인 전체 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/public")
    public ResponseEntity<List<MovieCardResponse>> getPublicMovieList() {
        return ResponseEntity.ok(movieUseCase.getPublicMovieList());
    }

    @Operation(summary = "장르별 영화 목록 조회", description = "특정 카테고리(장르)에 속한 공개 영화 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음")
    })
    @GetMapping("/genre/{categoryId}")
    public ResponseEntity<List<MovieCardResponse>> getMovieListByGenre(
            @Parameter(description = "카테고리 ID", required = true)
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(movieUseCase.getMovieListByGenre(categoryId));
    }

    @Operation(summary = "영화 검색",
            description = "ES 활성화 시: 제목/설명/크리에이터명 통합 검색(관련도 정렬, 오타 허용, 복수 카테고리 필터, 하이라이트). " +
                    "ES 비활성화 시: JPA LIKE 제목 검색.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/search")
    public ResponseEntity<List<MovieCardResponse>> searchMoviesByTitle(
            @Parameter(description = "검색 키워드 (미입력 시 전체 반환)")
            @RequestParam(required = false, defaultValue = "") String title,
            @Parameter(description = "카테고리 ID 복수 선택 (ES 활성화 시만 동작, 예: ?categoryIds=1&categoryIds=3)")
            @RequestParam(required = false) List<Long> categoryIds) {
        if (esEnabled && movieSearchUseCase != null) {
            Pageable pageable = PageRequest.of(0, 20);
            var esResult = movieSearchUseCase.searchMovies(
                    title.isBlank() ? null : title, categoryIds, pageable);
            List<MovieCardResponse> cards = esResult.items().stream()
                    .map(item -> new MovieCardResponse(
                            item.movieId(), null, item.creatorNickname(),
                            item.title(), null, null,
                            item.highlightedTitle(),
                            item.highlightedCreatorNickname()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(cards);
        }
        return ResponseEntity.ok(movieUseCase.searchMoviesByTitle(title));
    }
}