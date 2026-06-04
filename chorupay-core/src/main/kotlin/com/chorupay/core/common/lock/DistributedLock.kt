package com.chorupay.core.common.lock

import java.util.concurrent.TimeUnit

/**
 * 분산 락 선언 어노테이션.
 *
 * [설계 결정 — 어노테이션은 core, 구현(AOP)은 infrastructure]
 *  애플리케이션 서비스(core) 의 메서드에 이 어노테이션을 붙여 "이 작업은 분산락 보호가 필요하다"는
 *  의도를 선언한다. 실제 락 획득/해제 로직(Redisson)은 infrastructure 의 Aspect 가 담당한다.
 *  어노테이션을 core 에 둠으로써 core 가 infrastructure 를 의존하지 않아도 되어
 *  (infrastructure → core) 단방향 의존이 유지된다.
 *
 * [키 표현식]
 *  [key] 는 SpEL(Spring Expression Language) 로 평가된다. 메서드 파라미터를 참조하여
 *  지갑/사용자 단위로 락 범위를 좁힌다.
 *    예) @DistributedLock(key = "'wallet:' + #command.walletId")
 *
 * [정합성 보장 핵심]
 *  Aspect 는 락을 획득한 뒤 대상 메서드를 "새 트랜잭션(REQUIRES_NEW)" 안에서 실행하고,
 *  트랜잭션 커밋이 끝난 뒤에야 락을 해제한다. 이로써 "DB 반영 전 락 해제 → 다른 스레드가
 *  옛 값 조회" 라는 경합을 차단한다.
 *
 * @property key 락 키 SpEL 표현식. 동일 키에 대한 동시 진입을 직렬화한다.
 * @property waitTime 락 획득을 위해 대기하는 최대 시간. 초과 시 즉시 실패(Fast-Fail).
 * @property leaseTime 락 임대 시간. 이 시간이 지나면 자동 해제(데드락 방지).
 * @property timeUnit waitTime/leaseTime 의 시간 단위.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val key: String,
    val waitTime: Long = 3L,
    val leaseTime: Long = 5L,
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
)
