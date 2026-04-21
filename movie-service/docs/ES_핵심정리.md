# ES 검색 기능 - 핵심 정리 & 트러블슈팅

> 조원들이 빠르게 파악할 수 있도록, 핵심 설계 결정/개선 사항/주의점/트러블슈팅을 정리한 문서입니다.

---

## 1. 핵심 설계 결정

### 왜 Redis로 인기 검색어를 처리하나?

인기 검색어를 ES에서 처리할 수도 있지만(SearchKeywordDocument + terms aggregation), **Redis Sorted Set**을 채택했습니다.

| 비교 | ES 방식 | Redis 방식 (채택) |
|------|---------|-------------------|
| 저장 | 검색어마다 문서 1개 색인 | `ZINCRBY` 한 줄 |
| 조회 | terms aggregation (집계 쿼리) | `ZREVRANGE` (O(log N)) |
| 성능 | 검색마다 ES 색인 + 집계 부하 | 인메모리, 마이크로초 단위 |
| 자원 | ES 클러스터에 부담 추가 | Redis 1개 키로 충분 |

**결론**: 검색어 랭킹은 단순 카운팅이라 ES의 전문 검색 능력이 필요 없음 → Redis가 더 적합

### 기능 토글 설계 (`@ConditionalOnProperty`)

ES/Redis 관련 빈 전체에 `@ConditionalOnProperty(prefix="movie.elasticsearch", name="enabled", havingValue="true")`를 적용했습니다.

- `movie.elasticsearch.enabled=false` 하나로 **Controller, Service, Consumer, RedisConfig 전부 비활성화**
- ES/Redis 없는 환경에서도 movie-service 기본 기능(CRUD)은 정상 동작
- 적용된 클래스: `MovieSearchController`, `MovieSearchService`, `MovieIndexEventConsumer`, `RedisConfig`

---

## 2. 코드 개선 사항 요약

이번에 적용한 주요 개선 10가지:

| # | 개선 | 이유 |
|---|------|------|
| 1 | `@Transactional` 클래스 → 메서드 레벨 | 읽기/쓰기 세분화, 불필요한 트랜잭션 범위 축소 |
| 2 | `recordSearchKeyword()` `@Async` 처리 | 검색 응답 지연 방지 (인기검색어 기록은 비동기) |
| 3 | Kafka Consumer 에러 핸들링 분리 | JSON 파싱 실패(스킵) vs 비즈니스 에러(재시도) 구분 |
| 4 | `searchByCategory` must → filter | 카테고리 필터링에 스코어 계산 불필요 → filter로 성능 향상 |
| 5 | `indexMovie` 카테고리 순회 2번 → 1번 | categoryIds + categoryNames 한 번에 추출 |
| 6 | multi_match boost 적용 | `title^3 > creatorNickname^2 > description` 관련도 개선 |
| 7 | 키워드 없을 때 `match_all` 명시 | 암묵적 동작 대신 명시적 쿼리로 의도 명확화 |
| 8 | 자동완성에 `creatorNickname` 추가 | 크리에이터명으로도 자동완성 가능 |
| 9 | 인기검색어 Redis 방식 전환 | ES SearchKeywordDocument 삭제 → Redis Sorted Set |
| 10 | size 파라미터 1~100 제한 | 과도한 요청으로 인한 ES 부하 방지 |

---

## 3. 주의할 점

### `@Async`와 `@EnableAsync`

- `MovieServiceApplication`에 `@EnableAsync`가 선언되어 있어야 `@Async`가 동작합니다
- `recordSearchKeyword()`는 `@Async`로 실행되므로, **같은 클래스 내부에서 호출하면 프록시를 타지 않아 비동기가 안 됩니다**
- 현재는 Controller → UseCase 인터페이스로 호출하므로 정상 동작

### Kafka Consumer Group ID

- MovieIndexEventConsumer의 `groupId`는 `movie-search-service`입니다
- 기존 MovieService의 다른 Consumer들은 `movie-service` groupId를 사용합니다
- **서로 다른 groupId**이므로 같은 토픽을 구독해도 각각 독립적으로 메시지를 수신합니다

### ES 색인과 RDB 데이터 정합성

- ES는 RDB의 **비동기 사본**입니다 — 짧은 시간 동안 불일치가 발생할 수 있습니다
- Kafka 이벤트 발행 후 Consumer 처리까지 약간의 지연이 있으므로, 등록 직후 바로 검색하면 결과에 안 나올 수 있습니다
- 이는 이벤트 기반 아키텍처의 정상 동작(Eventual Consistency)입니다

---

## 4. 트러블슈팅 FAQ

### Q1. ES에 연결이 안 돼요

**증상**: `Connection refused: localhost:9200`

**확인 순서**:
1. ES가 실행 중인지 확인: `curl http://localhost:9200` → 버전 JSON이 나와야 함
2. Docker라면: `docker ps | grep elasticsearch`
3. `application-dev.yaml`의 `spring.elasticsearch.uris` 값 확인
4. 방화벽/포트 충돌 확인

**로컬 Docker 실행**:
```bash
docker run -d --name elasticsearch \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elasticsearch:8.x.x
```

### Q2. 검색이 안 돼요 (결과가 0건)

**확인 순서**:
1. `movie.elasticsearch.enabled=true`인지 확인
2. ES에 인덱스가 존재하는지: `curl http://localhost:9200/movies/_count`
3. 색인된 문서의 `visibility`가 `PUBLIC`인지 확인 (PRIVATE은 검색에서 필터링됨)
4. Kafka가 정상 동작 중인지 — Consumer 로그에 `[Kafka → ES]` 메시지가 찍히는지 확인
5. 수동 색인: `POST /api/movies/search/index/{movieId}` 호출 후 다시 검색

### Q3. 영화 등록했는데 바로 검색이 안 돼요

**원인**: Kafka 이벤트 기반 비동기 색인이므로 약간의 지연이 정상입니다.

**해결**:
- 보통 수 초 이내에 색인됩니다
- 급하면 수동 색인 API 호출: `POST /api/movies/search/index/{movieId}`
- Consumer 로그에서 `[Kafka → ES] 영화 색인 생성 완료` 메시지 확인

### Q4. 인기 검색어가 안 나와요

**확인 순서**:
1. Redis가 실행 중인지: `redis-cli ping` → `PONG`
2. Docker라면: `docker ps | grep redis`
3. `application-dev.yaml`의 `spring.data.redis.host/port` 확인
4. Redis에 데이터가 있는지: `redis-cli ZREVRANGE search:rank 0 -1 WITHSCORES`
5. 검색을 몇 번 한 후 다시 확인 (검색할 때 키워드가 기록됨)

**로컬 Docker 실행**:
```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### Q5. Kafka Consumer 에러 로그가 계속 찍혀요

**JSON 파싱 실패** (`JSON 파싱 실패 (스킵)` 로그):
- 다른 서비스가 잘못된 형식으로 메시지를 보내고 있을 수 있음
- 이 에러는 스킵되므로 서비스 동작에 영향 없음

**비즈니스 로직 실패** (예외 전파):
- ES 연결 오류 → Q1 확인
- movieId에 해당하는 영화가 RDB에 없음 → 삭제된 영화의 이벤트가 지연 도착한 경우

### Q6. 한글 검색이 잘 안 돼요

**원인**: ES의 `standard` analyzer는 한글 형태소 분석을 지원하지 않습니다. "인터스텔라"로는 검색되지만, "인터스"로 부분 검색은 통합 검색에서 매칭이 약할 수 있습니다.

**현재 대안**:
- 자동완성(`/autocomplete`)은 `matchPhrasePrefix`를 사용하므로 접두어 검색이 가능합니다
- "인터스" 입력 → 자동완성으로 "인터스텔라" 제안

**향후 개선 방향** (필요 시):
- `nori` 한글 형태소 분석기 플러그인 적용
- custom analyzer 설정 추가

---

## 5. 로컬 개발 체크리스트

ES 검색 기능을 로컬에서 테스트하려면:

- [ ] **Elasticsearch** 실행 (`localhost:9200`)
- [ ] **Redis** 실행 (`localhost:6379`)
- [ ] **Kafka** 실행 (bootstrap-servers 확인)
- [ ] `application-dev.yaml`에서 `movie.elasticsearch.enabled: true` 확인
- [ ] 서비스 시작 후 영화 등록 → 검색 확인
- [ ] Consumer 로그에서 `[Kafka → ES]` 메시지 확인
