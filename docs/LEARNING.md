# Chorupay 학습 노트 — 상황과 해결의 기록

> 이 문서는 Chorupay를 만들며 마주친 **문제 상황을 순서대로** 따라가면서,
> 각 상황에서 등장하는 **개념**과 이 프로젝트의 **해결 방법**을 정리한 학습 노트입니다.
> 각 절은 `[상황] → [개념] → [해결] → [코드 위치]` 순서로 구성됩니다.

---

## 목차

1. [프로젝트를 모듈로 나누는 이유 — 헥사고날 아키텍처](#1-프로젝트를-모듈로-나누는-이유--헥사고날-아키텍처)
2. [돈을 어떻게 표현할 것인가 — 도메인 모델링](#2-돈을-어떻게-표현할-것인가--도메인-모델링)
3. [잔액이 맞는지 어떻게 증명하나 — 복식부기 원장](#3-잔액이-맞는지-어떻게-증명하나--복식부기-원장)
4. [동시에 두 요청이 한 지갑을 수정하면 — 동시성과 분산락](#4-동시에-두-요청이-한-지갑을-수정하면--동시성과-분산락)
5. [락을 풀었는데 커밋이 안 됐다면 — 락과 트랜잭션의 순서](#5-락을-풀었는데-커밋이-안-됐다면--락과-트랜잭션의-순서)
6. [분산락이 뚫리는 날 — 낙관적 락 2차 방어](#6-분산락이-뚫리는-날--낙관적-락-2차-방어)
7. [같은 요청이 두 번 오면 — 멱등성](#7-같은-요청이-두-번-오면--멱등성)
8. [DB는 커밋됐는데 Kafka가 죽었다면 — Transactional Outbox](#8-db는-커밋됐는데-kafka가-죽었다면--transactional-outbox)
9. [트래픽이 몰려올 때 — Fast-Fail과 Rate Limiting](#9-트래픽이-몰려올-때--fast-fail과-rate-limiting)
10. [상태가 멋대로 바뀌지 않게 — 상태 머신](#10-상태가-멋대로-바뀌지-않게--상태-머신)
11. [남은 한계와 다음 실험 주제](#11-남은-한계와-다음-실험-주제)

---

## 1. 프로젝트를 모듈로 나누는 이유 — 헥사고날 아키텍처

### 상황

핀테크 도메인 로직(잔액 계산, 정합성 검증)은 서비스의 심장이다. 그런데 단일 모듈로 개발하면
도메인 코드 안에 `@Entity`, `RedisTemplate`, `KafkaTemplate` 이 스며들기 시작한다.
시간이 지나면 **"잔액 계산 로직을 테스트하려면 DB를 띄워야 하는"** 상태가 된다.
기술(JPA, Redis, Kafka)을 교체하거나 업그레이드할 때 도메인 코드까지 갈아엎어야 한다.

### 개념: 헥사고날 아키텍처 (Ports & Adapters) + 의존성 역전

- **포트(Port)**: 도메인이 바깥세상에 요구하는 것을 **인터페이스**로 선언한다.
  - 인바운드 포트: "나를 이렇게 호출해라" (`ChargeWalletUseCase`)
  - 아웃바운드 포트: "나는 이런 저장소가 필요하다" (`WalletRepository`)
- **어댑터(Adapter)**: 포트의 구현체. REST 컨트롤러(인바운드), JPA/Kafka(아웃바운드).
- **의존성 역전(DIP)**: 구현(infrastructure)이 추상(core)에 의존한다. 반대 방향 의존은 없다.

### 해결

Gradle 멀티 모듈로 의존 방향을 **컴파일 타임에 강제**했다:

```
chorupay-api ──▶ chorupay-infrastructure ──▶ chorupay-core
      └─────────────────────────────────────────▶ chorupay-core
```

- `chorupay-core` 의 의존성은 `spring-context`, `spring-tx` 수준으로 최소화. JPA/Redis/Kafka가 **클래스패스에 아예 없다.**
  → 도메인 코드에 `@Entity`를 붙이는 실수가 컴파일 에러로 차단된다.
- `WalletRepository` 인터페이스는 core가 정의하고, `WalletPersistenceAdapter`는 infrastructure가 구현한다.
- 덕분에 도메인 테스트(`WalletTest`, `PaymentTest`)는 **DB 없이 밀리초 단위로** 실행된다.

### 코드 위치

- 모듈 정의: `settings.gradle.kts`, 각 모듈의 `build.gradle.kts`
- 아웃바운드 포트: `chorupay-core/.../application/port/out/WalletRepository.kt`
- 구현 어댑터: `chorupay-infrastructure/.../persistence/wallet/WalletPersistenceAdapter.kt`

---

## 2. 돈을 어떻게 표현할 것인가 — 도메인 모델링

### 상황

금액을 `Long` 으로 그냥 다루면, `balance + userId` 같은 무의미한 연산이 컴파일되고,
음수 금액이 시스템 깊숙이 들어와서야 터진다. "원시 타입 집착(Primitive Obsession)" 문제다.

### 개념: Value Object + Kotlin value class

- **Value Object**: 식별자 없이 값 자체로 동등성이 결정되는 불변 객체. 생성 시점에 유효성을 강제한다.
- Kotlin의 `@JvmInline value class`는 런타임에 원시 타입으로 인라인되어 **래핑 비용이 0**이다.
  대규모 트래픽 실험에서 초당 수만 건의 금액 연산이 일어나도 객체 할당 부담이 없다.

### 해결

```kotlin
@JvmInline
value class Money(val amount: Long) : Comparable<Money> {
    init {
        if (amount < 0) throw InvalidAmountException(amount)
    }
    operator fun plus(other: Money): Money = Money(amount + other.amount)
    operator fun minus(other: Money): Money = Money(amount - other.amount)
}
```

- **음수 Money는 생성 자체가 불가능**하다. 방어 코드를 여기저기 흩뿌릴 필요가 없다.
- 통화는 KRW 고정이므로 소수점 없는 `Long`을 사용 (부동소수점 오차 원천 차단).

### 코드 위치

- `chorupay-core/.../domain/wallet/Money.kt`
- 예외 계층: `chorupay-core/.../domain/exception/DomainExceptions.kt` (sealed class — 에러 코드를 타입으로 강제)

---

## 3. 잔액이 맞는지 어떻게 증명하나 — 복식부기 원장

### 상황

`UPDATE wallets SET balance = balance + 1000` 만 반복하면, 잔액이 **왜 그 값인지 아무도 설명할 수 없다.**
버그·해킹·운영 실수로 잔액이 어긋나도 탐지할 방법이 없다. 금융 시스템에서 이것은 치명적이다.

### 개념: 복식부기(Double-Entry Bookkeeping)와 Append-Only 원장

- 모든 잔액 변경을 **CREDIT(+) / DEBIT(−) 기록**으로 남긴다. 기록은 절대 수정·삭제하지 않는다.
- 핵심 불변식(invariant): **`Σ(CREDIT) − Σ(DEBIT) == 현재 잔액`**
- 원장은 곧 감사 추적(audit trail)이며, 잔액은 원장의 **파생값**이라는 관점.

### 해결

도메인 메서드가 잔액 변경과 원장 기록을 **한 몸으로** 반환하도록 설계했다:

```kotlin
// Wallet.charge() 는 잔액을 바꾸면서 반드시 LedgerEntry를 함께 만든다.
// "원장 없는 잔액 변경"이 코드 구조상 불가능하다.
fun charge(amount: Money, transactionId: UUID, now: Instant): WalletMutationResult
```

그리고 정합성을 언제든 검증할 수 있다:

```kotlin
fun validateConsistency(ledgerEntries: List<LedgerEntry>) {
    val ledgerSum = ledgerEntries.sumOf { it.signedAmount() }
    if (ledgerSum != balance.amount) throw BalanceInconsistencyException(...)
}
```

이 예외는 API 레이어에서 **500 + `[ALERT]` 로그**로 처리된다. 돈이 어긋난 것은 4xx로 얼버무릴 일이 아니라
즉시 사람이 개입해야 할 장애이기 때문이다.

### 코드 위치

- 원장: `chorupay-core/.../domain/ledger/LedgerEntry.kt`
- 검증: `chorupay-core/.../domain/wallet/Wallet.kt` 의 `validateConsistency` / `isConsistentWith`
- 원장 합계 쿼리: `chorupay-infrastructure/.../persistence/ledger/LedgerJpaRepository.kt` (CASE WHEN JPQL)
- 테스트: `chorupay-core/src/test/.../WalletTest.kt` — `원장 합계와 잔액의 정합성이 유지된다`

---

## 4. 동시에 두 요청이 한 지갑을 수정하면 — 동시성과 분산락

### 상황 (이 프로젝트의 핵심 실험)

잔액 10,000원인 지갑에 **결제 요청 2건(각 8,000원)이 동시에** 들어온다:

```
스레드 A: 잔액 조회(10,000) → 충분! → 차감 → 잔액 2,000 저장
스레드 B: 잔액 조회(10,000) → 충분! → 차감 → 잔액 2,000 저장   ← A의 차감이 사라짐
```

결과: 16,000원을 썼는데 잔액은 2,000원. 전형적인 **Lost Update / Check-Then-Act 레이스**다.
`synchronized`는 서버가 한 대일 때만 통한다. 스케일아웃하면 JVM 락은 무력하다.

### 개념: 분산락 (Distributed Lock)

- 여러 서버가 공유하는 외부 저장소(Redis)에 락을 두어, **클러스터 전체에서 임계 구역을 직렬화**한다.
- Redisson의 `RLock`은 다음을 제공한다:
  - `waitTime`: 락 획득을 기다리는 최대 시간 (경합 시 무한 대기 방지)
  - `leaseTime`: 락 자동 만료 시간 (락 잡은 서버가 죽어도 데드락 방지)
  - Pub/Sub 기반 대기 (스핀락처럼 Redis를 두들기지 않음)
- 락의 **키 설계**가 핵심: 전역 락은 시스템 전체를 직렬화해버린다.
  → `wallet:charge:{userId}` 처럼 **사용자 단위**로 잠가서, 서로 다른 사용자는 완전히 병렬 처리.

### 해결

비즈니스 코드를 오염시키지 않도록 **어노테이션 + AOP**로 구현했다:

```kotlin
@DistributedLock(key = "'wallet:charge:' + #command.userId", waitTime = 3L, leaseTime = 5L)
override fun charge(command: ChargeWalletCommand): ChargeWalletResult { ... }
```

- 락 키는 SpEL로 메서드 파라미터에서 동적으로 만든다 (`CustomSpringElParser`).
- 결제(`PaymentService`)도 **같은 락 키**를 쓴다. 충전과 결제가 같은 지갑을 두고 경합하기 때문에
  락의 "보호 단위"는 기능이 아니라 **자원(지갑)** 이어야 한다.
- 락 획득 실패는 예외(`DistributedLockAcquisitionException`) → API에서 **429** 응답 (9절 Fast-Fail 참고).

**아키텍처 딜레마와 해법**: `@DistributedLock`을 서비스(core)에 붙이고 싶은데, Redisson은 infrastructure에 있다.
→ **어노테이션(계약)은 core에, Aspect(구현)는 infrastructure에** 두었다. core는 "이 메서드는 직렬화돼야 한다"는
의도만 선언하고, 어떻게 직렬화할지(Redis인지 ZooKeeper인지)는 모른다. 의존 방향이 깨지지 않는다.

### 코드 위치

- 어노테이션(core): `chorupay-core/.../common/lock/DistributedLock.kt`
- Aspect(infra): `chorupay-infrastructure/.../lock/DistributedLockAspect.kt`
- SpEL 파서: `chorupay-infrastructure/.../lock/CustomSpringElParser.kt`
- Redisson 설정: `chorupay-infrastructure/.../config/RedissonConfig.kt`

---

## 5. 락을 풀었는데 커밋이 안 됐다면 — 락과 트랜잭션의 순서

### 상황 (분산락에서 가장 많이 틀리는 부분)

`@Transactional` 메서드에 분산락 AOP를 그냥 얹으면 이런 순서가 된다:

```
락 획득 → 비즈니스 로직 → 락 해제 → ... → 트랜잭션 커밋
                              ↑
              이 틈에 다음 스레드가 락을 잡고 "커밋 전의 낡은 잔액"을 읽는다!
```

스프링의 트랜잭션 커밋은 메서드 리턴 **후** 트랜잭션 AOP에서 일어난다. 락 AOP가 트랜잭션 AOP보다
안쪽에 있으면, **락 해제가 커밋보다 먼저** 일어나는 레이스가 생긴다. 분산락을 걸었는데도 정합성이 깨지는,
디버깅하기 매우 어려운 버그다.

### 개념: 락의 범위가 트랜잭션을 완전히 감싸야 한다

올바른 순서는 반드시:

```
락 획득 → [ 트랜잭션 시작 → 로직 → 커밋 완료 ] → 락 해제
```

### 해결

두 가지 장치를 조합했다:

1. **`AopForTransaction`**: 락 Aspect가 비즈니스 로직을 직접 호출하지 않고,
   `@Transactional(propagation = REQUIRES_NEW)` 가 붙은 별도 빈을 거쳐 호출한다.
   → **새 트랜잭션이 락 안에서 시작되고, 락 안에서 커밋된다.** 메서드가 리턴되는 순간 커밋이 보장된다.

```kotlin
@Component
class AopForTransaction {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun proceed(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed()
}
```

2. **`@Order(Int.MIN_VALUE)`**: 락 Aspect를 가장 바깥에 배치해, 다른 어떤 AOP(트랜잭션 포함)보다
   먼저 락을 잡고 나중에 풀도록 한다.

3. 락 해제는 `finally`에서, 그리고 **`isHeldByCurrentThread` 확인 후** 해제한다.
   leaseTime 만료로 이미 락을 잃은 상태에서 unlock하면 `IllegalMonitorStateException`이 나기 때문이다.

```kotlin
finally {
    if (rLock.isHeldByCurrentThread) rLock.unlock()
}
```

### 코드 위치

- `chorupay-infrastructure/.../lock/AopForTransaction.kt`
- `chorupay-infrastructure/.../lock/DistributedLockAspect.kt` 의 `@Order(Int.MIN_VALUE)` 와 finally 블록

---

## 6. 분산락이 뚫리는 날 — 낙관적 락 2차 방어

### 상황

분산락도 완벽하지 않다. 대표 시나리오:

- GC pause나 네트워크 지연으로 로직이 `leaseTime`(5초)보다 오래 걸림 → 락이 자동 만료 →
  **다른 스레드가 락을 잡고 들어옴** → 두 스레드가 동시에 같은 지갑을 수정.
- Redis 장애/failover 순간의 락 유실.

"분산락을 믿고 끝"이라고 하면, 1년에 한 번 일어나는 이 상황에서 돈이 어긋난다.

### 개념: 낙관적 락 (Optimistic Lock) — 심층 방어(Defense in Depth)

- JPA `@Version`: 엔티티에 버전 컬럼을 두고, UPDATE 시 `WHERE version = 읽었던_버전` 조건을 붙인다.
- 그 사이 누가 먼저 수정했다면 영향받은 행이 0건 → `OptimisticLockingFailureException`.
- 분산락(비관적, 사전 차단)이 1차 관문, `@Version`(낙관적, 사후 탐지)이 **최후의 안전망**.
  보안의 심층 방어와 같은 사고방식이다.

### 해결

```kotlin
@Entity
class WalletJpaEntity(
    ...
    @Version
    var version: Long = 0,
)
```

여기서 함정 하나: 어댑터의 `save()`가 매번 `fromDomain()`으로 **새 엔티티 객체를 만들어 저장하면
버전 비교가 무의미해진다.** 그래서 기존 행은 **조회한 영속 엔티티의 필드를 변경(dirty checking)** 하는
방식으로 저장해, `@Version` 메커니즘이 실제로 동작하게 했다.

### 코드 위치

- `chorupay-infrastructure/.../persistence/wallet/WalletJpaEntity.kt` (`@Version`)
- `chorupay-infrastructure/.../persistence/wallet/WalletPersistenceAdapter.kt` 의 `save()` (기존 엔티티 변경 방식)

---

## 7. 같은 요청이 두 번 오면 — 멱등성

### 상황

모바일 앱에서 충전 버튼을 눌렀는데 응답이 느리다. 클라이언트는 타임아웃으로 재시도한다.
**서버는 첫 요청을 이미 처리했다.** 재시도가 그대로 처리되면 충전이 두 번 된다.
네트워크는 본질적으로 "요청이 처리됐는지" 알 수 없다 (Two Generals Problem).

### 개념: 멱등성 (Idempotency)

- 같은 연산을 여러 번 수행해도 결과가 한 번 수행한 것과 같도록 만드는 성질.
- 표준 패턴: 클라이언트가 **멱등키(idempotency key)** 를 생성해 보내고,
  서버는 같은 키의 요청을 두 번째부터는 처리하지 않고 기존 결과를 반환한다.

### 해결

두 API에서 서로 다른 구현 전략을 썼다 (둘 다 분산락 **안에서** 검사하므로 동시 중복 요청도 안전):

1. **결제**: `payments.idempotency_key` 에 **유니크 인덱스**. 같은 키가 오면 기존 Payment를 조회해 그대로 반환.
2. **충전**: 충전은 별도 엔티티가 없으므로, 멱등키로 **결정적(deterministic) UUID**를 만들어
   원장의 `transactionId`로 사용한다:

```kotlin
val transactionId = UUID.nameUUIDFromBytes("charge:${command.idempotencyKey}".toByteArray())
if (ledgerRepository.existsByTransactionId(transactionId)) {
    return ChargeWalletResult(...)  // 이미 처리됨 — 현재 상태 반환
}
```

같은 멱등키 → 항상 같은 transactionId → 원장 존재 여부로 중복 판정. 별도 테이블 없이 멱등성을 얻는다.

### 코드 위치

- `chorupay-core/.../application/service/WalletService.kt` (결정적 UUID)
- `chorupay-core/.../application/service/PaymentService.kt` (`findByIdempotencyKey`)
- `chorupay-infrastructure/.../persistence/payment/PaymentJpaEntity.kt` (유니크 인덱스)

---

## 8. DB는 커밋됐는데 Kafka가 죽었다면 — Transactional Outbox

### 상황

결제 성공 후 이벤트를 발행해야 한다 (알림, 정산, 통계...). 순진한 구현:

```kotlin
@Transactional
fun processPayment(...) {
    paymentRepository.save(payment)   // DB 트랜잭션
    kafkaTemplate.send(event)          // Kafka 발행
}
```

문제: DB와 Kafka는 **서로 다른 시스템이라 하나의 트랜잭션으로 묶을 수 없다** (이중 쓰기, dual write).

- 커밋 성공 + Kafka 실패 → **이벤트 유실** (결제는 됐는데 알림이 안 감)
- Kafka 발행 성공 + 커밋 롤백 → **유령 이벤트** (결제가 안 됐는데 알림이 감)

### 개념: Transactional Outbox 패턴

- 이벤트를 외부 브로커에 바로 쏘지 않고, **비즈니스 데이터와 같은 DB 트랜잭션으로
  `outbox_events` 테이블에 INSERT** 한다. → 원자성은 DB 트랜잭션이 보장.
- 별도 프로세스(릴레이)가 outbox 테이블을 폴링해 Kafka로 발행하고 SENT로 마킹한다.
- 전달 보장은 **at-least-once**: 발행 후 마킹 전에 죽으면 같은 이벤트가 두 번 나갈 수 있다.
  → **소비자가 `eventId`로 멱등 처리**하는 것까지가 패턴의 완성이다 (7절의 멱등성이 여기서 또 등장).

### 해결

```
[같은 트랜잭션] Payment 저장 + OutboxEvent(PENDING) 저장 → 커밋
        ↓ (1초 폴링)
OutboxScheduler: PENDING 조회 → Kafka 발행 → markSent(SENT)
                               발행 실패 → retryCount 증가, 5회 초과 시 FAILED (운영자 개입 대상)
```

디테일:

- `OutboxEventPublisherAdapter`에는 일부러 `@Transactional`을 붙이지 않았다 —
  **호출자(서비스)의 트랜잭션에 참여**해야 "같은 트랜잭션" 보장이 성립하기 때문.
- Kafka 파티션 키 = `aggregateId`(지갑 ID) → **같은 지갑의 이벤트는 순서가 보장**된다.
- 프로듀서는 `acks=all` + `enable.idempotence=true` + `max.in.flight=1` 로 브로커 측 중복/순서 문제를 차단.

### 코드 위치

- Outbox 엔티티/저장소: `chorupay-infrastructure/.../messaging/outbox/OutboxEvent.kt`, `OutboxEventRepository.kt`
- 발행 어댑터: `chorupay-infrastructure/.../messaging/outbox/OutboxEventPublisherAdapter.kt`
- 릴레이: `chorupay-infrastructure/.../messaging/outbox/OutboxScheduler.kt`
- Kafka 프로듀서: `chorupay-infrastructure/.../messaging/kafka/KafkaEventProducer.kt`, `config/KafkaProducerConfig.kt`

---

## 9. 트래픽이 몰려올 때 — Fast-Fail과 Rate Limiting

### 상황

이벤트로 충전 요청이 평소의 100배로 몰린다. 모든 요청을 받아주면:

- 톰캣 스레드 200개가 전부 분산락 대기에 묶인다 → 스레드 풀 고갈
- DB 커넥션 풀이 마른다 → **건강한 요청까지 전부 타임아웃**
- 시스템 전체가 연쇄적으로 무너진다 (cascading failure)

### 개념: Fast-Fail — "버틸 수 없으면 빨리, 싸게 거절하라"

처리 못 할 요청은 비싼 자원(스레드, DB 커넥션, 락)에 닿기 **전에** 거절하는 것이 전체 가용성을 지킨다.
요청이 거치는 방어선을 **싼 것부터** 배치한다:

```
① Rate Limiting 필터 (메모리 연산)      → 429
② Bean Validation (CPU 연산)           → 400
③ 분산락 waitTime 3초 제한 (Redis)      → 429
④ 도메인 검증 — 잔액 부족 등 (DB 조회 후) → 즉시 실패
```

### 개념: 토큰 버킷 (Token Bucket)

- 버킷에 초당 `refillRate`개씩 토큰이 차고, 최대 `capacity`까지 쌓인다. 요청마다 토큰 1개 소비.
- 고정 윈도우 방식과 달리 **순간 버스트(쌓인 토큰만큼)는 허용**하면서 평균 속도를 제한한다.
- 구현 포인트: 요청마다 호출되는 자료구조이므로 `synchronized` 대신
  **CAS(Compare-And-Swap) 루프 + 불변 상태 객체**로 락 없이(lock-free) 구현했다.
  rate limiter 자체가 병목이 되면 본말전도이기 때문이다.

```kotlin
// AtomicReference<State> — 읽기 → 새 상태 계산 → CAS, 실패하면 재시도
val state = stateRef.get()
val refilled = state.refill(now)
if (refilled.tokens >= 1 && stateRef.compareAndSet(state, refilled.consume())) return true
```

### 해결

- `RateLimitingFilter`(서블릿 필터, `@Order(1)`)가 클라이언트(IP)별 버킷으로 `/api/` 요청을 제한. 초과 시 429.
- 분산락 획득 실패(`waitTime` 3초 초과)도 무한 대기 대신 **429로 즉시 실패** —
  대기 행렬을 키우는 것보다 클라이언트에게 "잠시 후 재시도"를 알리는 편이 시스템을 지킨다.
- 잔액 부족은 DB 트랜잭션을 길게 끌지 않고 도메인에서 예외로 즉시 분기 →
  결제는 FAIL 상태로 기록하고 정상 응답(200 + status=FAIL)으로 반환. (비즈니스 결과이지 서버 오류가 아니다.)
- HikariCP `connection-timeout: 3000` — 커넥션 고갈 시 30초(기본값) 대기 대신 3초 만에 실패.

### 코드 위치

- `chorupay-api/.../ratelimit/TokenBucket.kt`, `RateLimitingFilter.kt`
- 예외 → HTTP 매핑: `chorupay-api/.../error/GlobalExceptionHandler.kt`
- DTO 검증: `chorupay-api/.../dto/WalletDtos.kt`, `PaymentDtos.kt`
- 풀 설정: `chorupay-api/src/main/resources/application.yml`

---

## 10. 상태가 멋대로 바뀌지 않게 — 상태 머신

### 상황

결제에는 생명주기가 있다: 생성(READY) → 성공/실패 → (성공한 건만) 취소.
상태를 `payment.status = SUCCESS` 처럼 자유롭게 대입할 수 있으면,
"이미 실패한 결제를 성공으로 바꾸는" 코드가 어디선가 반드시 작성된다. 돈과 직결된 버그다.

### 개념: 유한 상태 머신(FSM)을 타입으로 강제하기

- 각 상태가 **자신이 갈 수 있는 다음 상태의 집합**을 스스로 선언한다.
- 모든 상태 변경은 전이 규칙을 검사하는 **단일 관문**을 통과해야 한다.

### 해결

```kotlin
enum class PaymentStatus {
    READY    { override val allowedTransitions get() = setOf(SUCCESS, FAIL) },
    SUCCESS  { override val allowedTransitions get() = setOf(CANCELED) },
    FAIL     { override val allowedTransitions get() = emptySet<PaymentStatus>() },   // 종결
    CANCELED { override val allowedTransitions get() = emptySet<PaymentStatus>() };   // 종결
    abstract val allowedTransitions: Set<PaymentStatus>
}
```

- `Payment.status`의 setter는 private. `markSuccess()` / `markFail()` / `cancel()` 만 열려 있고,
  모두 private `transitionTo()` 를 거치며 허용 전이가 아니면 `IllegalPaymentStateTransitionException`.
- 새 상태를 추가하면 enum이 `allowedTransitions` 구현을 **컴파일 에러로 강제**한다 — 규칙 누락이 불가능.

### 코드 위치

- `chorupay-core/.../domain/payment/PaymentStatus.kt`, `Payment.kt`
- 테스트: `chorupay-core/src/test/.../PaymentTest.kt` (허용/금지 전이 5케이스)

---

## 11. 남은 한계와 다음 실험 주제

학습은 "현재 설계가 어디서 깨지는지"를 아는 데서 한 단계 더 나아간다. 이 스캐폴딩의 의도적 한계:

| 한계 | 설명 | 다음 실험 |
|---|---|---|
| **단일 Redis 분산락** | Redis 단일 인스턴스 장애 시 락 기능 전체 정지. Redlock 알고리즘은 그 자체로 논쟁이 있다 (Martin Kleppmann vs antirez 논쟁 참고) | Redis Sentinel/Cluster, 또는 DB 비관적 락(`SELECT ... FOR UPDATE`)과의 성능 비교 실험 |
| **Outbox 폴링 방식** | 1초 폴링은 지연과 DB 부하의 트레이드오프 | Debezium CDC(WAL 기반)로 폴링 제거 실험 |
| **레이트 리미터가 인스턴스 로컬** | 서버 N대면 실제 한도는 N배가 된다 | Redis 기반 분산 레이트 리미팅 (Redisson `RRateLimiter`) |
| **결제 취소의 잔액 복원 미구현** | `Payment.cancel()`은 상태만 바꾼다 | 보상 트랜잭션(compensation), 나아가 Saga 패턴 실험 |
| **잔액을 컬럼으로 유지** | 원장이 진실의 원천이라면 잔액은 파생값일 뿐 | 이벤트 소싱(Event Sourcing) — 원장 리플레이로 잔액을 재구성하는 모델 실험 |
| **동시성 테스트 부재** | 도메인 단위 테스트만 존재 | Testcontainers + `ExecutorService`로 N스레드 동시 충전/결제 → 최종 잔액 검증하는 통합 테스트 |

---

## 한눈에 보는 개념 지도

```
[요청 폭주]            → Fast-Fail, 토큰 버킷 Rate Limiting (9)
[중복 요청]            → 멱등성: 멱등키, 결정적 UUID, 유니크 제약 (7)
[동시 수정]            → 분산락(1차) + 낙관적 락(2차) (4, 6)
[락-커밋 순서 레이스]   → REQUIRES_NEW로 락 안에서 커밋 보장 (5)
[잔액 신뢰 문제]        → 복식부기 원장 + 불변식 검증 (3)
[DB-Kafka 이중 쓰기]   → Transactional Outbox + at-least-once + 소비자 멱등 (8)
[비정상 상태 전이]      → 타입으로 강제한 상태 머신 (10)
[기술-도메인 결합]      → 헥사고날: 포트/어댑터, 멀티 모듈로 의존 방향 강제 (1)
```
