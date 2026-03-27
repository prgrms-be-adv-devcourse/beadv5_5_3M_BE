package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.request.category.RegisterCategoryRequest;
import com.example.movieservice.presentation.dto.response.category.CategoryResponse;
import com.example.movieservice.presentation.dto.response.category.RegisterCategoryResponse;

import java.util.List;

public interface CategoryUseCase {
    List<CategoryResponse> getCategories();

    RegisterCategoryResponse register(RegisterCategoryRequest request);
}
