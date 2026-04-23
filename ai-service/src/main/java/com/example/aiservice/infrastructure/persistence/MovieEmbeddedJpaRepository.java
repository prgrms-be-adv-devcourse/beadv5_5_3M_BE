package com.example.aiservice.infrastructure.persistence;

import com.example.aiservice.domain.model.MovieEmbedded;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieEmbeddedJpaRepository extends JpaRepository<MovieEmbedded, Long> {

    // pgvector ANN 검색 (코사인 거리 <=>). queryVector는 "[x1,x2,...]" 형태 문자열로 전달.
    @Query(value = """
            SELECT movie_id AS movieId,
                   1 - (embedding <=> CAST(:queryVector AS vector)) AS similarity
            FROM movies_embedded
            WHERE is_public = true
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<AnnResultProjection> findAnnNeighbors(
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);

    @Query("SELECT m.movieId FROM MovieEmbedded m WHERE m.isPublic = true AND m.publishedAt IS NOT NULL ORDER BY m.publishedAt DESC")
    List<Long> findNewReleaseCandidates(Pageable pageable);

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
