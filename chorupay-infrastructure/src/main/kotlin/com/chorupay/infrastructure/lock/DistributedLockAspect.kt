package com.chorupay.infrastructure.lock

import com.chorupay.core.common.lock.DistributedLock
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Redisson 기반 분산락 Aspect.
 *
 * `@DistributedLock` 이 붙은 메서드를 가로채(@Around) 다음을 수행한다.
 *   1. SpEL 로 락 키를 동적으로 평가한다.
 *   2. Redisson RLock 으로 waitTime 동안 락 획득을 시도한다. (실패 시 즉시 Fast-Fail)
 *   3. 획득 성공 시, [AopForTransaction] 을 통해 "새 트랜잭션" 안에서 대상 로직을 실행한다.
 *      → 메서드 반환 시점엔 이미 커밋이 끝나 있다.
 *   4. finally 에서 (현재 스레드가 보유 중일 때만) 락을 해제한다.
 *
 * [동시성 정합성] 동일 지갑 키에 대한 충전/결제가 동시에 들어와도, 이 락이 쓰기를 직렬화하고
 * "커밋 후 해제" 를 보장하므로 Lost Update / 잔액 초과 차감(oversell) 이 발생하지 않는다.
 *
 * @Order(HIGHEST) 로 트랜잭션 Advice 보다 바깥에서 동작하도록 우선순위를 최상위로 둔다.
 */
@Aspect
@Component
@Order(Int.MIN_VALUE) // 가장 바깥 Advice = 락이 트랜잭션을 감싼다.
class DistributedLockAspect(
    private val redissonClient: RedissonClient,
    private val aopForTransaction: AopForTransaction,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Redis 키 네임스페이스 접두사. */
        private const val LOCK_PREFIX = "CHORUPAY_LOCK:"
    }

    @Around("@annotation(com.chorupay.core.common.lock.DistributedLock)")
    fun lock(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(DistributedLock::class.java)

        // 1) SpEL 키 평가
        val dynamicKey = CustomSpringElParser.getDynamicValue(
            parameterNames = signature.parameterNames,
            args = joinPoint.args,
            key = annotation.key,
        )
        val lockKey = LOCK_PREFIX + dynamicKey
        val rLock = redissonClient.getLock(lockKey)

        var acquired = false
        try {
            // 2) 락 획득 시도 (waitTime 초과 시 false → Fast-Fail)
            acquired = rLock.tryLock(
                annotation.waitTime,
                annotation.leaseTime,
                annotation.timeUnit,
            )
            if (!acquired) {
                log.warn("분산락 획득 실패(Fast-Fail). key={}", lockKey)
                throw DistributedLockAcquisitionException(lockKey)
            }

            log.debug("분산락 획득. key={}", lockKey)
            // 3) 새 트랜잭션 안에서 비즈니스 로직 실행 (커밋 완료 후 반환)
            return aopForTransaction.proceed(joinPoint)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DistributedLockAcquisitionException(lockKey, e)
        } finally {
            // 4) 현재 스레드가 보유한 락만 안전하게 해제 (leaseTime 만료로 이미 풀렸을 수 있음)
            if (acquired && rLock.isHeldByCurrentThread) {
                rLock.unlock()
                log.debug("분산락 해제. key={}", lockKey)
            }
        }
    }
}

/**
 * 분산락 획득 실패 예외.
 *
 * 대규모 트래픽 상황에서 경합이 심해 제한 시간 내 락을 얻지 못하면 발생한다.
 * API 계층은 이를 429(Too Many Requests) 로 변환하여 즉시 실패 응답한다.
 */
class DistributedLockAcquisitionException(
    val lockKey: String,
    cause: Throwable? = null,
) : RuntimeException("분산락 획득에 실패했습니다. key=$lockKey", cause)
