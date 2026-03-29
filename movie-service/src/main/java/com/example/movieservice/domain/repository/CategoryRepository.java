package com.example.movieservice.domain.repository;

import com.example.movieservice.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findById(Long categoryId);

    List<Category> findAll();

    Category save(Category category);

    boolean existsById(Long categoryId);
}
