package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieEmbedded;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieEmbeddedJpaRepository extends JpaRepository<MovieEmbedded, Long> {
}
