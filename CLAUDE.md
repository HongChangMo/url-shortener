# 절대 하지 말아야 할 것들
- 내 허락 없이 파일 삭제하지 마
- 모르면 추측하지 말고 물어봐
- 작업 중간에 임의로 다른 방향으로 바꾸지 마

# project spec
- 도메인 중심 패키지 구조 (DDD 방식)
- `interfaces/api/`: Controllers, DTOs, API specs (Swagger)
- `application/`: Facades orchestrating domain services, Info objects
- `domain/`: Entities, Value Objects, Domain Services, Repository interfaces
- `infrastructure/`: JPA repositories, Feign clients, external integrations

# 테스트 전략 (Test Pyramid)
레이어별 무조건적인 테스트를 강요하지 않는다. 아래 피라미드 구조로 충분하다.

- **Unit Test** — `domain/`, `application/` 레이어
  - Spring 컨텍스트 없음, Mockito만 사용
  - 예: `UrlServiceTest`, `ShortCodeGeneratorTest`
- **Integration Test** — `infrastructure/` 레이어
  - `@DataJpaTest`, `@DataRedisTest` + Testcontainers
  - 예: `UrlJpaRepositoryTest`, `UrlCacheServiceTest`
- **E2E Test** — `interfaces/api/` 레이어 (Controller)
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers (PostgreSQL + Valkey)
  - 실제 HTTP 요청으로 전체 스택 검증
  - 예: `UrlApiE2ETest`