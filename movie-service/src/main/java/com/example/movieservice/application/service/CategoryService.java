package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.CategoryUseCase;
import com.example.movieservice.domain.model.Category;
import com.example.movieservice.domain.repository.CategoryRepository;
import com.example.movieservice.presentation.dto.request.RegisterCategoryRequest;
import com.example.movieservice.presentation.dto.response.CategoryResponse;
import com.example.movieservice.presentation.dto.response.RegisterCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService implements CategoryUseCase {
    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(c-> new CategoryResponse(c.getCategoryId(), c.getName()))
                .toList();
    }

    @Override
    @Transactional
    public RegisterCategoryResponse register(RegisterCategoryRequest request) {
        Category category = Category.builder()
                .name(request.name())
                .build();
        return new RegisterCategoryResponse(categoryRepository.save(category).getCategoryId());
    }
}
