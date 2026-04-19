package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieEmbedded;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieEmbeddedJpaRepository extends JpaRepository<MovieEmbedded, Long> {

    @Modifying
    @Query("UPDATE MovieEmbedded m SET m.isPublic = true WHERE m.movieId = :movieId")
    void setPublic(@Param("movieId") Long movieId);

    @Modifying
    @Query("UPDATE MovieEmbedded m SET m.publishedAt = CURRENT_TIMESTAMP WHERE m.movieId = :movieId AND m.publishedAt IS NULL")
    void setPublishedAtIfAbsent(@Param("movieId") Long movieId);

    @Modifying
    @Query("UPDATE MovieEmbedded m SET m.isPublic = false WHERE m.movieId = :movieId")
    void unpublishMovie(@Param("movieId") Long movieId);
}
