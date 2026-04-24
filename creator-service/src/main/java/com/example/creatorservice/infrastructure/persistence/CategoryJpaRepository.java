package com.example.creatorservice.infrastructure.persistence;

import com.example.creatorservice.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
    boolean existsByNameIgnoreCase(String name);
}