package com.example.movieservice.infrastructure.elasticsearch;

import com.example.movieservice.infrastructure.elasticsearch.document.MovieDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

// 앱 시작 시 movies 인덱스가 없으면 MovieDocument 의 @Field 매핑으로 명시 생성한다.
// 이렇게 하지 않으면 첫 색인 요청에서 ES dynamic mapping 이 작동해
// visibility 가 text 로 잡혀 검색의 term 필터가 매칭되지 않는 사고가 재발할 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movie.elasticsearch", name = "enabled", havingValue = "true")
public class MovieIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations esOperations;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOps = esOperations.indexOps(MovieDocument.class);
        if (indexOps.exists()) {
            log.info("[ES] 'movies' 인덱스 이미 존재 — 매핑 변경 없이 통과");
            return;
        }
        indexOps.createWithMapping();
        log.info("[ES] 'movies' 인덱스 생성 + @Field 매핑 적용 완료");
    }
}
