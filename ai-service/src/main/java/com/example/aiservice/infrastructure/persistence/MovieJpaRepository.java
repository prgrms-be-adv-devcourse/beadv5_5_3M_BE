package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
}
