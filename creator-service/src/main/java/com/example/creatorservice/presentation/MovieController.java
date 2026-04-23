package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.MovieUploadUseCase;
import com.example.creatorservice.presentation.dto.req.RegisterMovieRequest;
import com.example.creatorservice.presentation.dto.req.UpdateMovieDetailRequest;
import com.example.creatorservice.presentation.dto.req.UpdateVisibilityRequest;
import com.example.creatorservice.presentation.dto.res.MovieDetailResponse;
import com.example.creatorservice.presentation.dto.res.MovieListItemResponse;
import com.example.creatorservice.presentation.dto.res.SchedulableMovieResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Movie", description = "영화 업로드/관리 API (크리에이터 전용)")
@RestController
@RequestMapping("/api/creators/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieUploadUseCase movieUploadUseCase;

    @Operation(summary = "영화 등록", description = "이미지와 영상 파일을 포함한 영화를 등록합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Long> register(
            @RequestHeader("X-Creator-Id") String creatorId,
            @RequestPart("request") @Valid RegisterMovieRequest request,
            @RequestPart("image") MultipartFile image,
            @RequestPart("video") MultipartFile video) {
        Long movieId = movieUploadUseCase.register(UUID.fromString(creatorId), request, image, video);
        return ResponseEntity.status(HttpStatus.CREATED).body(movieId);
    }

    @Operation(summary = "공개/비공개 전환", description = "영화의 공개 여부를 변경합니다.")
    @PatchMapping("/{movieId}/visibility")
    public ResponseEntity<Void> updateVisibility(
            @RequestHeader("X-Creator-Id") String creatorId,
            @PathVariable Long movieId,
            @RequestBody @Valid UpdateVisibilityRequest request) {
        movieUploadUseCase.updateVisibility(UUID.fromString(creatorId), movieId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "영화 상세 수정", description = "제목, 설명, 추가 쿠키를 수정합니다.")
    @PatchMapping("/{movieId}/detail")
    public ResponseEntity<Void> updateDetail(
            @RequestHeader("X-Creator-Id") String creatorId,
            @PathVariable Long movieId,
            @RequestBody @Valid UpdateMovieDetailRequest request) {
        movieUploadUseCase.updateDetail(UUID.fromString(creatorId), movieId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "영화 삭제", description = "확정된 상영 일정이 없는 영화를 삭제합니다.")
    @DeleteMapping("/{movieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Creator-Id") String creatorId,
            @PathVariable Long movieId) {
        movieUploadUseCase.delete(UUID.fromString(creatorId), movieId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "영화 상세 조회 (수정용)", description = "크리에이터 전용 영화 상세 정보를 조회합니다.")
    @GetMapping("/{movieId}/detail")
    public ResponseEntity<MovieDetailResponse> getDetail(
            @RequestHeader("X-Creator-Id") String creatorId,
            @PathVariable Long movieId) {
        return ResponseEntity.ok(movieUploadUseCase.getDetail(UUID.fromString(creatorId), movieId));
    }

    @Operation(summary = "내 영화 목록", description = "내가 등록한 영화 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<MovieListItemResponse>> getMyMovies(
            @RequestHeader("X-Creator-Id") String creatorId) {
        return ResponseEntity.ok(movieUploadUseCase.getMyMovies(UUID.fromString(creatorId)));
    }

    @Operation(summary = "스케줄 편성 가능 영화 목록", description = "확정된 상영 일정이 없는 영화 목록을 조회합니다.")
    @GetMapping("/schedulable")
    public ResponseEntity<List<SchedulableMovieResponse>> getSchedulableMovies(
            @RequestHeader("X-Creator-Id") String creatorId) {
        return ResponseEntity.ok(movieUploadUseCase.getSchedulableMovies(UUID.fromString(creatorId)));
    }
}