package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.RegisterCategoryRequest;
import com.example.movieservice.presentation.dto.response.CategoryResponse;
import com.example.movieservice.presentation.dto.response.RegisterCategoryResponse;

import java.util.List;

public interface CategoryUseCase {
    List<CategoryResponse> getCategories();

    RegisterCategoryResponse register(RegisterCategoryRequest request);
}
