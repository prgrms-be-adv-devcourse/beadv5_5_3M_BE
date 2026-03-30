package com.example.movieservice.presentation.controller;

import com.example.movieservice.application.usecase.CategoryUseCase;
import com.example.movieservice.presentation.dto.request.category.RegisterCategoryRequest;
import com.example.movieservice.presentation.dto.response.category.CategoryResponse;
import com.example.movieservice.presentation.dto.response.category.RegisterCategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Category", description = "카테고리 관련 API")
@RestController
@RequestMapping("/api/movies/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryUseCase categoryUseCase;

    @Operation(summary = "카테고리 목록 조회", description = "등록된 모든 카테고리 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(){
        return ResponseEntity.ok(categoryUseCase.getCategories());
    }

    @Operation(summary = "카테고리 등록", description = "새로운 카테고리를 등록합니다. 생성된 categoryId를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공, 생성된 categoryId 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterCategoryResponse> register(
            @Valid @RequestBody RegisterCategoryRequest request
    ){
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryUseCase.register(request));
    }
}
