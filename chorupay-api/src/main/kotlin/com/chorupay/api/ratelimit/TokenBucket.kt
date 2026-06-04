package com.chorupay.api.ratelimit

import java.util.concurrent.atomic.AtomicReference

/**
 * 토큰 버킷 (Token Bucket) 레이트 리미터 - 스레드 안전.
 *
 * [동작]
 *  - 버킷은 최대 [capacity] 개의 토큰을 담는다.
 *  - 초당 [refillTokensPerSecond] 개씩 토큰이 채워진다.
 *  - 요청 1건당 토큰 1개를 소비한다. 토큰이 없으면 거부(false) → 호출자는 429 로 Fast-Fail.
 *
 * CAS(compare-and-set) 루프로 락 없이 동시성을 처리하여, 고-RPS 환경에서도 경합 비용이 작다.
 *
 * @property capacity 버킷 용량(순간 최대 허용량 = 버스트 한도)
 * @property refillTokensPerSecond 초당 토큰 보충량(지속 처리율)
 */
class TokenBucket(
    private val capacity: Double,
    private val refillTokensPerSecond: Double,
) {
    /** (현재 토큰 수, 마지막 보충 시각 nanos) 스냅샷. 불변 객체를 원자적으로 교체한다. */
    private data class State(val tokens: Double, val lastRefillNanos: Long)

    private val state = AtomicReference(State(capacity, System.nanoTime()))

    /**
     * 토큰 1개 소비를 시도한다.
     * @return 허용되면 true, 토큰 부족이면 false
     */
    fun tryConsume(): Boolean {
        while (true) {
            val now = System.nanoTime()
            val current = state.get()

            // 경과 시간만큼 토큰 보충 (capacity 상한)
            val elapsedSeconds = (now - current.lastRefillNanos) / 1_000_000_000.0
            val refilled = (current.tokens + elapsedSeconds * refillTokensPerSecond)
                .coerceAtMost(capacity)

            if (refilled < 1.0) {
                // 보충해도 1개 미만 → 거부. 시각만 갱신해 다음 계산 기준을 맞춘다.
                val denied = State(refilled, now)
                if (state.compareAndSet(current, denied)) return false
                // CAS 실패 시 재시도
                continue
            }

            val next = State(refilled - 1.0, now)
            if (state.compareAndSet(current, next)) return true
            // CAS 실패 시 재시도 (다른 스레드가 먼저 변경)
        }
    }
}
