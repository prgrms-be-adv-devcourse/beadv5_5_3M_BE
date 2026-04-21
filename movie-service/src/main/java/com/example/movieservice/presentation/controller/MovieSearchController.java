package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.MovieSearchUseCase;
import com.example.movieservice.presentation.dto.response.movie.AutocompleteResponse;
import com.example.movieservice.presentation.dto.response.movie.CategoryFilterCountResponse;
import com.example.movieservice.presentation.dto.response.movie.PopularKeywordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "영화 검색", description = "Elasticsearch 기반 영화 검색 API")
@RestController
@RequestMapping("/api/movies/search")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movie.elasticsearch", name = "enabled", havingValue = "true")
public class MovieSearchController {

    private final MovieSearchUseCase movieSearchUseCase;

    @Operation(summary = "영화 색인 생성", description = "특정 영화를 Elasticsearch에 색인합니다. (관리용)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "색인 생성 성공")
    })
    @PostMapping("/index/{movieId}")
    public ResponseEntity<Void> indexMovie(
            @Parameter(description = "색인할 영화 ID", required = true)
            @PathVariable Long movieId) {
        movieSearchUseCase.indexMovie(movieId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "영화 색인 삭제", description = "특정 영화를 Elasticsearch에서 삭제합니다. (관리용)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "색인 삭제 성공")
    })
    @DeleteMapping("/index/{movieId}")
    public ResponseEntity<Void> deleteMovieIndex(
            @Parameter(description = "삭제할 영화 ID", required = true)
            @PathVariable Long movieId) {
        movieSearchUseCase.deleteMovieIndex(movieId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "자동완성", description = "제목/크리에이터명 접두어를 기반으로 영화 후보를 자동완성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자동완성 결과 반환")
    })
    @GetMapping("/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @Parameter(description = "입력 접두어 (예: '인터스')", example = "인터스")
            @RequestParam String prefix,

            @Parameter(description = "최대 결과 수", example = "5")
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(movieSearchUseCase.autocomplete(prefix, size));
    }

    @Operation(summary = "인기 검색어 조회", description = "검색 횟수가 많은 인기 검색어 Top N을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인기 검색어 목록 반환")
    })
    @GetMapping("/popular")
    public ResponseEntity<PopularKeywordResponse> getPopularKeywords(
            @Parameter(description = "조회할 인기 검색어 수", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(movieSearchUseCase.getPopularKeywords(size));
    }

    @Operation(summary = "필터 카운트 조회", description = "카테고리별 영화 수를 집계합니다. 프론트에서 '드라마(7) 액션(6)' 같은 필터 UI에 사용합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "필터 카운트 반환")
    })
    @GetMapping("/filters")
    public ResponseEntity<CategoryFilterCountResponse> getFilterCounts() {
        return ResponseEntity.ok(movieSearchUseCase.getFilterCounts());
    }
}
