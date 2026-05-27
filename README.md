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

앱 기동 후 아래 명령어로 실행합니다.

```bash
# Smoke — 기본 동작 확인 (1 VU, 30s)
docker compose --profile smoke up k6-smoke

# Load — 일반 부하 (최대 50 VU, 5m)
docker compose --profile load up k6-load

# Stress — 최대 부하 (최대 300 VU, 10m)
docker compose --profile stress up k6-stress
```
