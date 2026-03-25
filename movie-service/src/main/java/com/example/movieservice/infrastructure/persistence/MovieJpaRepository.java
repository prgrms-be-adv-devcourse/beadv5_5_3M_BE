package com.example.movieservice.infrastructure.persistence;

import com.example.movieservice.domain.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovieJpaRepository extends JpaRepository<Movie, Long> {
    long countByCreatorId(UUID creatorId);

}
