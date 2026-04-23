package com.example.creatorservice.application.usecase;

import com.example.creatorservice.presentation.dto.req.RegisterCategoryRequest;
import com.example.creatorservice.presentation.dto.res.CategoryResponse;

import java.util.List;

public interface CategoryUseCase {
    Long register(RegisterCategoryRequest request);
    List<CategoryResponse> getCategories();
}