package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
}
