package com.example.movieservice.infrastructure.elasticsearch;

import com.example.movieservice.application.usecase.MovieSearchUseCase;
import com.example.movieservice.domain.model.Movie;
import com.example.movieservice.domain.repository.MovieRepository;
import com.example.movieservice.infrastructure.elasticsearch.document.MovieDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.List;

// 앱 시작 시 movies 인덱스 매핑을 보장하고, 인덱스가 비어있으면
// DB의 PUBLIC 영화로 자가복구(백필)한다.
//
// 매핑 보장: 첫 색인 요청에서 ES dynamic mapping 이 작동해 visibility 가 text 로
//           잡히는 사고(2026-04-29) 재발 방지.
// 자가복구: ES PVC 가 비거나 운영자가 인덱스를 밀어 docs 가 0건이고 DB에는 영화가
//          남아있는 사고(2026-04-30) 시 pod 재시작 한 번으로 복구.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movie.elasticsearch", name = "enabled", havingValue = "true")
public class MovieIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations esOperations;
    private final MovieSearchRepository movieSearchRepository;
    private final MovieRepository movieRepository;
    private final MovieSearchUseCase movieSearchUseCase;

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexMapping();
        backfillIfEmpty();
    }

    private void ensureIndexMapping() {
        IndexOperations indexOps = esOperations.indexOps(MovieDocument.class);
        if (indexOps.exists()) {
            log.info("[ES] 'movies' 인덱스 이미 존재 — 매핑 변경 없이 통과");
            return;
        }
        indexOps.createWithMapping();
        log.info("[ES] 'movies' 인덱스 생성 + @Field 매핑 적용 완료");
    }

    // ES docs 0건일 때만 동작. PUBLIC 영화가 이미 한 건이라도 ES 에 있으면 건너뜀
    // (운영 중 부분 손실은 Kafka 이벤트 재처리로 다루는 것이 옳음).
    private void backfillIfEmpty() {
        long esCount = movieSearchRepository.count();
        if (esCount > 0) {
            log.info("[ES] 자가복구 건너뜀 — docs 가 이미 {}건 존재", esCount);
            return;
        }

        List<Movie> publicMovies = movieRepository.findAllPublic();
        if (publicMovies.isEmpty()) {
            log.info("[ES] 자가복구 건너뜀 — DB 에 PUBLIC 영화 0건");
            return;
        }

        log.info("[ES] 자가복구 시작: PUBLIC 영화 {}건 → ES 색인", publicMovies.size());
        int success = 0;
        int failed = 0;
        for (Movie movie : publicMovies) {
            try {
                movieSearchUseCase.indexMovie(movie.getMovieId());
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("[ES] 자가복구 실패 movieId={} - {}", movie.getMovieId(), e.getMessage());
            }
        }
        log.info("[ES] 자가복구 완료 — 성공 {}, 실패 {}", success, failed);
    }
}
