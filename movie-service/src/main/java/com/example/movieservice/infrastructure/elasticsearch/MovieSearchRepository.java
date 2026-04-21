package com.example.movieservice.infrastructure.elasticsearch;

import com.example.movieservice.infrastructure.elasticsearch.document.MovieDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MovieSearchRepository extends ElasticsearchRepository<MovieDocument, String> {
}
