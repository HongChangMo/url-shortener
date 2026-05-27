# Claude Progress Log

## Current Verified State

| 항목 | 내용 |
|------|------|
| **Repository root** | `/Users/zzangmo/project/url` |
| **Standard startup** | `docker compose up --build` |
| **Standard verification** | `./gradlew :shortener:test` |
| **Highest priority unfinished feature** | `api-server` (priority 9) — 검증용 httpbin 서버 추가 |
| **Current blocker** | 없음 |

---

## Session Records

### Session 1 — Phase 1 MVP 구현

- **Goal:** URL 단축 서비스 기본 기능 구현 (shorten, redirect, cache)
- **Completed:**
  - Gradle 멀티모듈 구성 (common/, shortener/)
  - PostgreSQL + Flyway 마이그레이션 (V1__create_urls_table.sql)
  - Url Entity, UrlJpaRepository
  - ShortCodeGenerator (Sqids, minLength=6)
  - ValkeyConfig, UrlCacheService (Cache-Aside, TTL 24h)
  - UrlService (shorten, redirect, access_count)
  - UrlController (POST /api/v1/data/shorten, GET /api/v1/{shortCode})
  - GlobalExceptionHandler (404, 410)
  - E2E 테스트 (UrlApiE2ETest — @SpringBootTest RANDOM_PORT + Testcontainers)
  - k6 smoke/load/stress 스크립트
  - docker-compose.yml 단순화 (shortener + postgres + valkey + k6 profiles)
- **Verification run:** `./gradlew :shortener:test` — BUILD SUCCESSFUL
- **Evidence recorded:** UrlApiE2ETest, UrlCacheServiceTest, UrlRepositoryTest, ShortCodeGeneratorTest 통과
- **Commits:** PR #1 merged to main (`5d60182`)
- **Known risks:** access_count 동기 DB UPDATE — 고트래픽 시 병목 가능
- **Next best action:** Rate Limiting 구현 (Phase 2)

---

### Session 2 — Rate Limiting 구현

- **Goal:** Bucket4j 토큰 버킷 기반 Rate Limiting, Docker 빌드 환경 구성
- **Completed:**
  - Bucket4j 8.14.0 (CAS 기반, Valkey 저장) Rate Limiting 구현
  - RateLimitProperties (Java record, @ConfigurationProperties)
  - RateLimitService (IP별 독립 버킷, rate:shorten:{ip} / rate:redirect:{ip})
  - RateLimitInterceptor (X-Forwarded-For 우선, /api/v1/** 적용)
  - RateLimitExceededException → 429 Too Many Requests
  - RateLimitServiceTest (@DataRedisTest + Testcontainers)
  - UrlApiE2ETest#shorten_exceedRateLimit_returns429 추가
  - k6 스크립트 VU별 X-Forwarded-For IP 분산 적용
  - shortener/Dockerfile 멀티스테이지 빌드 추가
  - .dockerignore 추가
  - docker-compose.yml build context 루트로 변경, DB_HOST 환경변수화
  - application.yml DB_HOST 환경변수 적용
  - k6 BASE_URL 기본값 localhost:8080으로 변경 (로컬 실행 지원)
  - README.md k6 --no-deps 실행 방법 추가
- **Verification run:** `./gradlew :shortener:test` — BUILD SUCCESSFUL
- **Evidence recorded:** RateLimitServiceTest + shorten_exceedRateLimit_returns429 통과
- **Commits:** PR #2 merged to main (`7d65b9f`, `2cc6544`)
- **Known risks:** 없음
- **Next best action:** api-server (httpbin) 추가로 302→200 전체 체인 검증

---

### Session 3 — 작업 관리 체계 전환

- **Goal:** harness/tasks.json → harness/feature_list.json 포맷 마이그레이션
- **Completed:**
  - harness/feature_list.json 생성 (13개 feature, passing/not_started 상태 반영)
  - harness/tasks.json 삭제
  - harness/claude-progress.md 생성
  - GitHub repository명 urlshortener → url-shortener 변경에 따른 remote URL 업데이트
- **Verification run:** 없음 (관리 파일 작업)
- **Evidence recorded:** —
- **Commits:** 미커밋
- **Known risks:** 없음
- **Next best action:** `api-server` feature 구현 (docker-compose에 httpbin 추가, k6 스크립트 대상 URL 변경)
