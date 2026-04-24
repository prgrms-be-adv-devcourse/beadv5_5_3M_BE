package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.CategoryUseCase;
import com.example.creatorservice.presentation.dto.req.RegisterCategoryRequest;
import com.example.creatorservice.presentation.dto.res.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import java.util.List;

@Tag(name = "Category", description = "카테고리 관리 API (크리에이터 전용)")
@RestController
@RequestMapping("/api/creators/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryUseCase categoryUseCase;

    @Operation(summary = "카테고리 등록")
    @PostMapping
    public ResponseEntity<Long> register(
            @RequestHeader("X-Creator-Id") String creatorId,
            @Valid @RequestBody RegisterCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryUseCase.register(request));
    }

    @Operation(summary = "카테고리 목록 조회")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryUseCase.getCategories());
    }
}