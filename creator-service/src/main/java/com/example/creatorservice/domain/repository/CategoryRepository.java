package com.example.creatorservice.domain.repository;

import com.example.creatorservice.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findById(Long categoryId);
    List<Category> findAll();
    Category save(Category category);
    boolean existsByNameIgnoreCase(String name);
}