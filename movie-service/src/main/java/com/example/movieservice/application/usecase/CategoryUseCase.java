package com.example.movieservice.application.usecase;

import com.example.movieservice.presentation.dto.response.category.CategoryResponse;

import java.util.List;

public interface CategoryUseCase {
    List<CategoryResponse> getCategories();
}
