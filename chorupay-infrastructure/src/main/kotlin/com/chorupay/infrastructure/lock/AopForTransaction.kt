package com.chorupay.infrastructure.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 분산락과 트랜잭션의 "경계 순서" 를 보장하기 위한 헬퍼.
 *
 * [왜 별도 빈으로 분리하는가 — 정합성의 핵심]
 *  분산락 흐름은 반드시 다음 순서여야 한다.
 *      락 획득 → (트랜잭션 시작 → 비즈니스 로직 → 트랜잭션 커밋) → 락 해제
 *
 *  만약 한 메서드 안에서 @Transactional 과 락을 함께 처리하면, 프록시 적용 순서에 따라
 *  "트랜잭션 커밋 전에 락이 풀려" 다른 스레드가 아직 커밋되지 않은(옛) 잔액을 읽는 경합이 생긴다.
 *
 *  이를 막기 위해 실제 비즈니스 로직 실행을 [Propagation.REQUIRES_NEW] 의 독립 트랜잭션으로
 *  감싼 별도 빈으로 분리한다. Aspect 는 이 빈을 호출하므로 "메서드 반환 = 커밋 완료" 가 보장되고,
 *  그 후에 락을 해제한다.
 */
@Component
class AopForTransaction {

    /**
     * 대상 조인포인트를 새로운 트랜잭션 안에서 실행한다.
     * 이 메서드가 정상 반환되면 트랜잭션은 이미 커밋된 상태다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun proceed(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed()
}
