package com.example.aiservice.domain.repository;

import com.example.aiservice.domain.model.AnnResult;
import com.example.aiservice.domain.model.MovieEmbedded;

import java.util.Collection;
import java.util.List;

public interface MovieEmbeddedRepository {

    long count();

    List<MovieEmbedded> findAllByIds(Collection<Long> movieIds);

    List<AnnResult> findAnnNeighbors(float[] queryVector, int limit);

    List<Long> findNewReleaseCandidates(int limit);

    boolean existsById(Long movieId);

    MovieEmbedded findById(Long movieId);

    MovieEmbedded save(MovieEmbedded movieEmbedded);

    void deleteById(Long movieId);

    void publishMovie(Long movieId);

    void unpublishMovie(Long movieId);
}
