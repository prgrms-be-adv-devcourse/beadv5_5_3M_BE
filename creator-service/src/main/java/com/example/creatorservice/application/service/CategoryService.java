package com.example.creatorservice.application.service;

import com.example.creatorservice.application.exception.MovieException;
import com.example.creatorservice.application.usecase.CategoryUseCase;
import com.example.creatorservice.domain.model.Category;
import com.example.creatorservice.domain.repository.CategoryRepository;
import com.example.creatorservice.presentation.dto.req.RegisterCategoryRequest;
import com.example.creatorservice.presentation.dto.res.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService implements CategoryUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public Long register(RegisterCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new MovieException(HttpStatus.CONFLICT, "CATEGORY_DUPLICATED", "이미 존재하는 카테고리입니다: " + request.name());
        }
        Category category = Category.builder().name(request.name()).build();
        return categoryRepository.save(category).getCategoryId();
    }

    @Override
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getName()))
                .toList();
    }
}