package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
    boolean existsByNameIgnoreCase(String name);
}
