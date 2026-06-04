# Chorupay (초루페이)

대규모 트래픽과 극한의 데이터 정합성을 실험하기 위한 핀테크 백엔드 스캐폴딩.
**헥사고날 아키텍처 + DDD** 기반의 멀티 모듈 프로젝트입니다.

> 📚 **학습 노트**: 이 프로젝트가 다루는 문제 상황과 해결 과정을 순차적으로 정리한
> [docs/LEARNING.md](docs/LEARNING.md) 를 참고하세요.

## 기술 스택
- Kotlin 1.9.25 / JVM 21
- Spring Boot 3.3.2, Spring Data JPA
- PostgreSQL, Redis(Redisson 분산락), Kafka
- Gradle (Kotlin DSL), 멀티 모듈

## 모듈 구조 / 의존 방향

```
chorupay-api ──▶ chorupay-infrastructure ──▶ chorupay-core
      └────────────────────────────────────────▶ chorupay-core
```

| 모듈 | 역할 |
|------|------|
| `chorupay-core` | 순수 도메인(Wallet, LedgerEntry, Payment) + 애플리케이션 유스케이스 + 포트. 프레임워크 의존 최소화. |
| `chorupay-infrastructure` | 아웃바운드 어댑터: JPA 영속성, Redisson 분산락 AOP, Kafka Transactional Outbox. |
| `chorupay-api` | 인바운드 어댑터: REST Controller, DTO 검증, 전역 예외 핸들러, Rate Limiting. 부트 실행 모듈. |

## 핵심 설계 포인트

### 1. 동시성 데이터 정합성 (2중 방어)
- **1차**: Redis 분산락(`@DistributedLock`, AOP). 동일 사용자 지갑의 충전/결제 쓰기를 직렬화.
  - 어노테이션은 `core`, 구현(`DistributedLockAspect`)은 `infrastructure` → 단방향 의존 유지.
  - **락 획득 → (새 트랜잭션 → 로직 → 커밋) → 락 해제** 순서를 `AopForTransaction`(REQUIRES_NEW)으로 보장.
- **2차**: JPA `@Version` 낙관적 락으로 Lost Update를 DB 레벨에서 탐지.
- **검증**: 복식부기 원장 합계 `Σ(CREDIT) - Σ(DEBIT) == balance` 를 `Wallet.validateConsistency`로 상시 점검.

### 2. Transactional Outbox
- 도메인 이벤트를 비즈니스 데이터와 **같은 트랜잭션**으로 `outbox_events`에 적재 → 이중 쓰기 문제 제거.
- `OutboxScheduler`가 PENDING 이벤트를 폴링하여 Kafka 발행(at-least-once, 소비자는 `eventId`로 멱등 처리).

### 3. Fast-Fail / Rate Limiting
- `RateLimitingFilter`(토큰 버킷, CAS 기반)가 컨트롤러 진입 전 과도한 요청을 429로 차단.
- 분산락 경합 실패도 429로 즉시 실패. Bean Validation으로 잘못된 요청을 도메인 이전에 차단.

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v1/wallets/{userId}/charge` | 지갑 충전 (멱등) |
| POST | `/api/v1/payments` | 결제 요청 (멱등) |

## 빌드 & 실행

```bash
# 빌드 (테스트 포함)
./gradlew build

# 도메인 단위 테스트만
./gradlew :chorupay-core:test

# 실행 (PostgreSQL/Redis/Kafka 필요 — 환경변수로 접속 정보 주입)
./gradlew :chorupay-api:bootRun
```

주요 환경변수: `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`,
`REDIS_HOST/REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS` (기본값은 localhost). 자세한 내용은
`chorupay-api/src/main/resources/application.yml` 참고.
