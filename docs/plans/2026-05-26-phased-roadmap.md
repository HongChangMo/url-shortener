# 단계별 개발 로드맵 (인프라 확장 중심)

## 개요

대규모 트래픽 환경을 가정한 URL 단축 서비스를 MVP부터 AWS 프로덕션까지 4단계로 점진적으로 확장한다.
각 단계는 인프라 변경을 최소화하면서 이전 단계의 안정성을 검증한 뒤 확장하는 방식을 따른다.

---

## Phase 1 — 단일 서버 MVP

### 인프라

```
[클라이언트]
     │
[shortener:8080]   ← 인스턴스 1개, Nginx 없음
  ┌──┴──────────┐
[Valkey]    [PostgreSQL]
```

Docker Compose 구성: `shortener` + `postgres` + `valkey`

### 포함 기능

| 항목 | 내용 |
|------|------|
| API | `POST /api/v1/data/shorten`, `GET /api/v1/{shortCode}` |
| 캐싱 | Cache-Aside (Valkey) |
| access_count | 동기 업데이트 (직접 DB UPDATE) |
| Secrets | 환경변수 직접 주입 |
| 단축 코드 생성 | Sqids (Feistel + Base62) |

### 제외 항목

- Nginx / 로드밸런서
- Rate Limiting
- SQS / Lambda / Secrets Manager
- Cache Warming
- CloudWatch

### 목표

비즈니스 로직(shorten + redirect + cache) 동작을 인프라 노이즈 없이 검증한다.

---

## Phase 2 — 성능 계층 (단일 서버 유지)

### 인프라

Phase 1 그대로 + LocalStack (SQS, Lambda, Secrets Manager)

```
[클라이언트]
     │
[shortener:8080]
  ┌──┴──────────┐
[Valkey]    [PostgreSQL]
     │
  [SQS] ← LocalStack
     │
  [Lambda] ← LocalStack
[Secrets Manager] ← LocalStack
```

### 추가 기능

| 항목 | 내용 |
|------|------|
| Rate Limiting | 토큰 버킷 (URL 생성) + 슬라이딩 윈도우 (redirect), Valkey Lua 스크립트 |
| Cache Warming | 서버 시작 시 access_count DESC Top N Valkey 선적재 |
| access_count | SQS → Lambda 비동기 배치 처리 (1분 주기 bulk UPDATE) |
| 만료 URL 정리 | Lambda + CloudWatch Events (1일 주기 DELETE) |
| Secrets | Secrets Manager에서 Feistel salt + DB 자격증명 로드 |

### 목표

스케일 아웃 이전에 성능 패턴(Rate Limit, 비동기 배치, 캐시 워밍)을 단일 서버에서 먼저 안정화한다.
이중화와 분리하여 디버깅 복잡도를 낮춘다.

---

## Phase 3 — Nginx 이중화 (로컬 HA)

### 인프라

```
[클라이언트]
     │
  [Nginx]  ← least_conn 로드밸런서
  ┌──┴──┐
[shortener-1] [shortener-2]
  ┌──┴──────────┐
[Valkey]    [PostgreSQL]
     │
  [SQS / Lambda / Secrets Manager] ← LocalStack
[CloudWatch] ← LocalStack
```

Docker Compose: Phase 2 전체 + `nginx` + `shortener-2`

### 추가 항목

| 항목 | 내용 |
|------|------|
| Nginx | least_conn 알고리즘, 헬스체크, 장애 서버 자동 제외 |
| shortener-2 | Phase 2와 동일 이미지, 인스턴스만 추가 |
| CloudWatch | 로그 수집 (LocalStack) |

### 목표

Stateless 설계 검증: 어느 인스턴스로 라우팅되어도 동일한 응답이 보장됨을 확인한다.

---

## Phase 4 — AWS 전환 (프로덕션 시뮬레이션)

### 인프라 대응표

| 로컬 (Phase 3) | AWS |
|----------------|-----|
| Nginx | ALB (Application Load Balancer) |
| Docker Compose | ECS Fargate |
| PostgreSQL 컨테이너 | RDS PostgreSQL + Read Replica |
| Valkey 컨테이너 | ElastiCache for Valkey |
| LocalStack SQS/Lambda/Secrets | AWS SQS / Lambda / Secrets Manager |
| LocalStack CloudWatch | AWS CloudWatch |

### 추가 항목

| 항목 | 내용 |
|------|------|
| IaC | Terraform 또는 AWS CDK |
| RDS Read Replica | 읽기/쓰기 분리 (redirect → Replica, write → Primary) |
| ALB | 헬스체크 + 타겟 그룹 |
| 전환 방식 | `AWS_ENDPOINT` 환경변수 제거 → 실제 AWS 엔드포인트 사용 |

### 목표

코드 변경 없이 환경변수 전환만으로 LocalStack → 실제 AWS 가동을 확인한다.

---

## 단계별 요약

| 단계 | 핵심 변경 | 검증 포인트 |
|------|-----------|------------|
| Phase 1 | 단일 서버, 기본 기능 | 비즈니스 로직 정확성 |
| Phase 2 | 성능 패턴 추가 (Rate Limit, 비동기 배치) | 성능 계층 안정성 |
| Phase 3 | Nginx + 인스턴스 2개 | Stateless 수평 확장 |
| Phase 4 | AWS 전환 | 프로덕션 운영 가능성 |
