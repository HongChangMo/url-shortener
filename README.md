# URL Shortener

Spring Boot 기반 URL 단축 서비스

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.4 |
| Database | PostgreSQL 16 + Flyway |
| Cache | Valkey 8 (Redis fork) |
| Build | Gradle 8 (멀티모듈) |
| Test | JUnit 5 + Testcontainers |
| Performance | k6 |
| Test API Server | httpbin (kennethreitz/httpbin) |

## 모듈 구조

```
url/
├── common/          # 공유 코드 (예외 클래스 등)
└── shortener/       # URL 단축 서비스
    └── src/main/java/com/urlshortener/
        ├── interfaces/api/     # Controller, DTO
        ├── application/        # Service (UrlService)
        ├── domain/             # Entity, Repository 인터페이스
        └── infrastructure/     # JPA, Cache, ShortCodeGenerator
```

## API

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/v1/data/shorten` | URL 단축 |
| `GET` | `/api/v1/{shortCode}` | 원래 URL로 302 리다이렉트 |

### 요청/응답 예시

```bash
# 단축 URL 생성
curl -X POST http://localhost:8080/api/v1/data/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/very/long/url"}'

# 응답
{"shortCode": "abc123", "shortUrl": "/api/v1/abc123"}

# 리다이렉트
curl -v http://localhost:8080/api/v1/abc123
# → 302 Location: https://example.com/very/long/url
```

## 인프라 구성

```
[k6] → [shortener:8080] → 302 redirect → [api-server:9090]
              ↕                    ↕
          [Valkey]           [PostgreSQL]
```

| 서비스 | 포트 | 설명 |
|--------|------|------|
| shortener | 8080 | URL 단축 서비스 |
| postgres | 5432 | 데이터베이스 |
| valkey | 6379 | 캐시 (Redis fork) |
| api-server | 9090 | 리다이렉트 검증용 httpbin |

## 로컬 실행

### 사전 요구사항

- Docker & Docker Compose
- Java 21
- Gradle 8

### 실행

```bash
# 인프라 기동 (postgres + valkey)
docker compose up postgres valkey -d

# 앱 실행
./gradlew :shortener:bootRun
```

또는 전체 docker compose로 실행:

```bash
docker compose up --build
```

## 테스트

```bash
# 전체 테스트
./gradlew :shortener:test

# 특정 테스트 클래스
./gradlew :shortener:test --tests "com.urlshortener.interfaces.api.UrlApiE2ETest"
```

### 테스트 구조 (Test Pyramid)

| 레이어 | 방식 | 예시 |
|--------|------|------|
| Unit | Mockito (Spring 컨텍스트 없음) | `UrlServiceTest`, `ShortCodeGeneratorTest` |
| Integration | `@DataJpaTest` / `@DataRedisTest` + Testcontainers | `UrlJpaRepositoryTest`, `UrlCacheServiceTest` |
| E2E | `@SpringBootTest(RANDOM_PORT)` + Testcontainers | `UrlApiE2ETest` |

## 성능 테스트 (k6)

`docker compose up --build`로 앱을 먼저 기동한 뒤 아래 명령어로 실행합니다.

이미 실행 중인 컨테이너가 있을 경우 `--no-deps` 플래그로 k6 컨테이너만 단독 실행합니다.

```bash
# Smoke — 기본 동작 + 예외/Rate Limiting 검증 (1 VU, 30s)
docker compose --profile smoke up --no-deps k6-smoke

# Load — 일반 부하 (최대 50 VU, 5m)
docker compose --profile load up --no-deps k6-load

# Stress — 최대 부하 (최대 300 VU, 10m)
docker compose --profile stress up --no-deps k6-stress
```

k6 스크립트는 `api-server`(httpbin)를 대상 URL로 사용하여 아래 전체 체인을 검증합니다:

```
k6 → POST /shorten (originalUrl: api-server/anything/...) → shortCode 발급
k6 → GET /{shortCode} → 302 redirect → api-server → 200 OK
```

### Smoke 테스트 검증 항목

| 시나리오 | 검증 내용 |
|---------|---------|
| URL 생성 | `POST /shorten` → 200, shortCode 존재 |
| 리다이렉트 | `GET /{code}` → 302, Location 헤더 존재 |
| 최종 응답 | 리다이렉트 따라가기 → 200 |
| 존재하지 않는 코드 | `GET /nonexistent000` → 404 |
| Rate Limiting | 동일 IP로 shorten 연속 요청 → 429 발생 확인 |

> **참고**: `shortener` 서비스에 healthcheck가 설정되어 있습니다. `--no-deps` 없이 전체 compose로 실행할 경우 Spring Boot가 완전히 기동된 후 k6가 시작됩니다.
