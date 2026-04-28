package com.example.movieservice.application.service;

import com.example.movieservice.application.usecase.MovieSearchUseCase;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.CreatorRepository;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.global.exception.ErrorStatus;
import com.example.movieservice.global.exception.GeneralException;
import com.example.movieservice.infrastructure.elasticsearch.MovieSearchRepository;
import com.example.movieservice.infrastructure.elasticsearch.document.MovieDocument;
import com.example.movieservice.presentation.dto.response.movie.AutocompleteResponse;
import com.example.movieservice.presentation.dto.response.movie.CategoryFilterCountResponse;
import com.example.movieservice.presentation.dto.response.movie.MovieSearchItemResponse;
import com.example.movieservice.presentation.dto.response.movie.MovieSearchResponse;
import com.example.movieservice.presentation.dto.response.movie.PopularKeywordResponse;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movie.elasticsearch", name = "enabled", havingValue = "true")
public class MovieSearchService implements MovieSearchUseCase {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final MovieSearchRepository movieSearchRepository;
    private final StringRedisTemplate redisTemplate;
    private final MovieRepository movieRepository;
    private final CreatorRepository creatorRepository;

    /** Redis Sorted Set 키 — 인기 검색어 랭킹 저장용 */
    private static final String SEARCH_RANK_KEY = "search:rank";

    @Override
    public MovieSearchResponse searchMovies(String keyword, List<Long> categoryIds, Pageable pageable) {
        // 검색어가 있으면 Redis 인기검색어 랭킹에 기록 (@Async로 비동기 처리 — 검색 응답 지연 X)
        if (keyword != null && !keyword.isBlank()) {
            recordSearchKeyword(keyword);
        }

        var nativeQueryBuilder = NativeQuery.builder()
                .withQuery(q -> {
                    q.bool(b -> {
                        // 키워드가 있으면 multi_match + fuzziness로 검색 (오타 허용)
                        if (keyword != null && !keyword.isBlank()) {
                            b.must(m -> m.multiMatch(mm -> mm
                                    .query(keyword)
                                    .fields("title^3", "creatorNickname^2", "description")
                                    .fuzziness("AUTO")
                            ));

                            // should: 제목에 키워드가 "phrase"(단어 순서 그대로)로 들어있으면 점수 가산
                            //   - 예: 검색어 "인터스텔라" → title에 정확히 "인터스텔라" 있으면 +2.0배
                            //   - 부분 매칭보다 완전 일치를 상위 노출시키는 효과
                            b.should(s -> s.matchPhrase(mp -> mp
                                    .field("title")
                                    .query(keyword)
                                    .boost(2.0f)
                            ));
                            // should: 크리에이터 닉네임 phrase 매칭 시 가산
                            b.should(s -> s.matchPhrase(mp -> mp
                                    .field("creatorNickname")
                                    .query(keyword)
                                    .boost(1.5f)
                            ));
                        } else {
                            b.must(m -> m.matchAll(ma -> ma));
                        }

                        // 공개 영화만
                        b.filter(f -> f.term(t -> t.field("visibility").value("PUBLIC")));

                        // must_not: "19금" 카테고리는 일반 검색에서 제외 (성인 콘텐츠 차단)
                        //   - categoryNames.keyword는 정확 매칭용 서브필드 (keyword 타입)
                        //   - 대소문자/공백 구분 정확 매칭
                        b.mustNot(mn -> mn.term(t -> t
                                .field("categoryNames.keyword")
                                .value("19금")
                        ));

                        // 카테고리 복수 필터 (terms 쿼리): categoryIds IN [1, 3, 5]
                        if (categoryIds != null && !categoryIds.isEmpty()) {
                            List<FieldValue> values = categoryIds.stream()
                                    .map(FieldValue::of)
                                    .collect(Collectors.toList());
                            b.filter(f -> f.terms(t -> t
                                    .field("categoryIds")
                                    .terms(v -> v.value(values))
                            ));
                        }
                        return b;
                    });
                    return q;
                });

        nativeQueryBuilder.withPageable(pageable);

        // 키워드 검색 시 _score(관련도) 정렬
        if (keyword != null && !keyword.isBlank()) {
            nativeQueryBuilder.withSort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
        }

        // Highlight 설정: title, creatorNickname에서 매칭 부분을 <em>로 감싸서 반환
        HighlightParameters highlightParams = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withNumberOfFragments(0) // 0이면 전체 필드값을 하이라이트해서 반환
                .build();
        Highlight highlight = new Highlight(
                highlightParams,
                List.of(
                        new HighlightField("title", HighlightFieldParameters.builder().build()),
                        new HighlightField("creatorNickname", HighlightFieldParameters.builder().build())
                )
        );
        nativeQueryBuilder.withHighlightQuery(new HighlightQuery(highlight, MovieDocument.class));

        var response = elasticsearchTemplate.search(nativeQueryBuilder.build(), MovieDocument.class);
        var items = response.getSearchHits().stream()
                .map(hit -> {
                    String highlightedTitle = extractHighlight(hit.getHighlightFields(), "title");
                    String highlightedNickname = extractHighlight(hit.getHighlightFields(), "creatorNickname");
                    return new MovieSearchItemResponse(
                            hit.getContent().getMovieId(),
                            hit.getContent().getTitle(),
                            hit.getContent().getCreatorNickname(),
                            highlightedTitle,
                            highlightedNickname
                    );
                })
                .collect(Collectors.toList());

        return new MovieSearchResponse(response.getTotalHits(), items);
    }

    /**
     * ES Highlight 결과(List<String>)에서 첫 조각을 꺼낸다.
     * 매칭 없으면 null 반환.
     */
    private String extractHighlight(java.util.Map<String, List<String>> highlightFields, String fieldName) {
        if (highlightFields == null) return null;
        List<String> fragments = highlightFields.get(fieldName);
        if (fragments == null || fragments.isEmpty()) return null;
        return fragments.get(0);
    }

    /**
     * Swagger 수동 색인용 (로컬 테스트) — DB에서 읽어서 ES에 저장
     * 운영에서는 Kafka 메시지(movie.es.uploaded)로 대체
     */
    @Override
    public void indexMovie(Long movieId) {
        Movie movie = movieRepository.findByMovieId(movieId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MOVIE_NOT_FOUND));

        String creatorNickname = creatorRepository.findById(movie.getCreatorId())
                .map(creator -> creator.getNickname())
                .orElse("");

        List<Long> categoryIds = new ArrayList<>();
        List<String> categoryNames = new ArrayList<>();
        for (var category : movie.getCategories()) {
            categoryIds.add(category.getCategoryId());
            categoryNames.add(category.getName());
        }

        MovieDocument doc = MovieDocument.builder()
                .id(movieId.toString())
                .movieId(movieId)
                .title(movie.getTitle())
                .description(movie.getDescription())
                .creatorNickname(creatorNickname)
                .imageUrl(movie.getImageUrl())
                .categoryIds(categoryIds)
                .categoryNames(categoryNames)
                .visibility(movie.getVisibility().name())
                .build();

        movieSearchRepository.save(doc);
    }

    /**
     * Kafka 메시지로 ES 색인 생성
     * topic: movie.es.uploaded
     */
    @Override
    public void indexMovieFromMessage(Long movieId, String title, String description,
                                      String creatorId, String creatorNickname,
                                      String imageUrl,
                                      List<Long> categoryIds, List<String> categoryNames) {
        MovieDocument doc = MovieDocument.builder()
                .id(movieId.toString())
                .movieId(movieId)
                .title(title)
                .description(description)
                .creatorNickname(creatorNickname)
                .imageUrl(imageUrl)
                .categoryIds(categoryIds != null ? new ArrayList<>(categoryIds) : new ArrayList<>())
                .categoryNames(categoryNames != null ? new ArrayList<>(categoryNames) : new ArrayList<>())
                .visibility("PUBLIC")
                .build();
        movieSearchRepository.save(doc);
    }

    /**
     * Kafka 메시지로 ES 색인 업데이트 (제목/설명/카테고리 변경)
     * topic: movie.es.updated
     */
    @Override
    public void updateMovieIndex(Long movieId, String title, String description,
                                 List<Long> categoryIds, List<String> categoryNames) {
        movieSearchRepository.findById(movieId.toString()).ifPresent(doc -> {
            MovieDocument updated = MovieDocument.builder()
                    .id(doc.getId())
                    .movieId(doc.getMovieId())
                    .title(title)
                    .description(description)
                    .creatorNickname(doc.getCreatorNickname())
                    .imageUrl(doc.getImageUrl())
                    .categoryIds(categoryIds != null ? new ArrayList<>(categoryIds) : doc.getCategoryIds())
                    .categoryNames(categoryNames != null ? new ArrayList<>(categoryNames) : doc.getCategoryNames())
                    .visibility(doc.getVisibility())
                    .build();
            movieSearchRepository.save(updated);
        });
    }

    /**
     * ES visibility 업데이트 (PUBLIC ↔ PRIVATE)
     * topic: movie.es.hidden
     */
    @Override
    public void updateMovieVisibility(Long movieId, String visibility) {
        movieSearchRepository.findById(movieId.toString()).ifPresent(doc -> {
            MovieDocument updated = MovieDocument.builder()
                    .id(doc.getId())
                    .movieId(doc.getMovieId())
                    .title(doc.getTitle())
                    .description(doc.getDescription())
                    .creatorNickname(doc.getCreatorNickname())
                    .imageUrl(doc.getImageUrl())
                    .categoryIds(doc.getCategoryIds())
                    .categoryNames(doc.getCategoryNames())
                    .visibility(visibility)
                    .build();
            movieSearchRepository.save(updated);
        });
    }

    @Override
    public void deleteMovieIndex(Long movieId) {
        movieSearchRepository.deleteById(movieId.toString());
    }

    // ═══════════════════════════════════════════════════
    // 확장 기능 1: 자동완성 (Autocomplete)
    // ═══════════════════════════════════════════════════

    /**
     * search_as_you_type + bool_prefix를 사용한 자동완성.
     *
     * title.autocomplete / creatorNickname.autocomplete 의 메인 + _2gram + _3gram 서브필드를
     * multi_match(bool_prefix)로 조회하여 "봉준" → "봉준호감독" 같은 부분 매칭을 지원한다.
     */
    @Override
    public AutocompleteResponse autocomplete(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) {
            return new AutocompleteResponse(List.of());
        }

        var nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(prefix)
                                .type(TextQueryType.BoolPrefix)
                                .fields(
                                        "title.autocomplete",
                                        "title.autocomplete._2gram",
                                        "title.autocomplete._3gram",
                                        "creatorNickname.autocomplete",
                                        "creatorNickname.autocomplete._2gram",
                                        "creatorNickname.autocomplete._3gram"
                                )
                        ))
                        // 공개 영화만
                        .filter(f -> f.term(t -> t.field("visibility").value("PUBLIC")))
                ))
                .withPageable(PageRequest.of(0, size)) // 최대 size개만 반환
                .build();

        var response = elasticsearchTemplate.search(nativeQuery, MovieDocument.class);

        var suggestions = response.getSearchHits().stream()
                .map(hit -> new AutocompleteResponse.AutocompleteItem(
                        hit.getContent().getMovieId(),
                        hit.getContent().getTitle(),
                        hit.getContent().getCreatorNickname()
                ))
                .collect(Collectors.toList());

        return new AutocompleteResponse(suggestions);
    }

    // ═══════════════════════════════════════════════════
    // 확장 기능 2: 인기 검색어 (Redis Sorted Set)
    // ═══════════════════════════════════════════════════

    /**
     * Redis Sorted Set으로 검색어 점수를 +1 합니다.
     *
     * Redis 명령어: ZINCRBY search:rank 1 "인터스텔라"
     *
     * @Async로 비동기 처리 — 검색 API 응답을 지연시키지 않습니다.
     */
    @Async
    @Override
    public void recordSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        try {
            String normalizedKeyword = keyword.trim().toLowerCase();
            redisTemplate.opsForZSet().incrementScore(SEARCH_RANK_KEY, normalizedKeyword, 1);
            log.debug("[인기검색어] Redis 기록 완료: {} (ZINCRBY)", normalizedKeyword);
        } catch (Exception e) {
            log.warn("[인기검색어] Redis 기록 실패 (검색 기능에는 영향 없음): {}", e.getMessage());
        }
    }

    /**
     * Redis Sorted Set에서 인기 검색어 Top N을 조회합니다.
     *
     * Redis 명령어: ZREVRANGE search:rank 0 {size-1} WITHSCORES
     * → 점수(검색 횟수)가 높은 순서대로 상위 N개 반환
     */
    @Override
    public PopularKeywordResponse getPopularKeywords(int size) {
        // ZREVRANGE: 점수 높은 순(내림차순)으로 상위 size개 조회
        Set<ZSetOperations.TypedTuple<String>> topKeywords =
                redisTemplate.opsForZSet().reverseRangeWithScores(SEARCH_RANK_KEY, 0, size - 1);

        List<PopularKeywordResponse.KeywordCount> keywords = new ArrayList<>();

        if (topKeywords != null) {
            for (ZSetOperations.TypedTuple<String> tuple : topKeywords) {
                keywords.add(new PopularKeywordResponse.KeywordCount(
                        tuple.getValue(),                          // 검색어
                        tuple.getScore() != null ? tuple.getScore().longValue() : 0  // 검색 횟수
                ));
            }
        }

        return new PopularKeywordResponse(keywords);
    }

    /**
     * 카테고리별 영화 수를 집계.
     * 프론트에서 "드라마(7) 액션(6)" 같은 필터 UI에 사용.
     */
    @Override
    public CategoryFilterCountResponse getFilterCounts() {
        var nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("visibility").value("PUBLIC")))
                .withAggregation("category_counts",
                        Aggregation.of(a -> a.terms(t -> t
                                .field("categoryNames.keyword")
                                .size(50)
                        ))
                )
                .withMaxResults(0)
                .build();

        var response = elasticsearchTemplate.search(nativeQuery, MovieDocument.class);

        List<CategoryFilterCountResponse.CategoryCount> categories = new ArrayList<>();
        ElasticsearchAggregations aggs = (ElasticsearchAggregations) response.getAggregations();
        if (aggs != null) {
            var catAgg = aggs.get("category_counts").aggregation().getAggregate();
            List<StringTermsBucket> catBuckets = catAgg.sterms().buckets().array();
            for (StringTermsBucket bucket : catBuckets) {
                categories.add(new CategoryFilterCountResponse.CategoryCount(
                        bucket.key().stringValue(),
                        bucket.docCount()
                ));
            }
        }

        return new CategoryFilterCountResponse(categories, List.of());
    }
}
