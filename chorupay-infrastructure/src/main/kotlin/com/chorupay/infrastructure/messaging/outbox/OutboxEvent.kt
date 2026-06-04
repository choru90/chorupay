package com.chorupay.infrastructure.messaging.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Outbox 이벤트의 발행 상태.
 */
enum class OutboxStatus {
    /** 적재됨. 아직 Kafka 미발행. */
    PENDING,

    /** Kafka 발행 완료. */
    SENT,

    /** 재시도 한계 초과 등으로 발행 실패(데드레터 후보). */
    FAILED,
}

/**
 * Transactional Outbox 이벤트 엔티티.
 *
 * [핵심 아이디어]
 *  비즈니스 데이터(지갑/원장/결제)와 "같은 트랜잭션" 으로 이 행을 INSERT 한다. 따라서
 *  - 비즈니스 커밋 성공 → 이벤트도 반드시 적재됨 (유실 없음)
 *  - 비즈니스 롤백 → 이벤트도 함께 롤백됨 (유령 이벤트 없음)
 *  실제 Kafka 발행은 [OutboxScheduler] 가 PENDING 행을 폴링하여 비동기로 수행한다.
 *  소비자는 [eventId] 로 멱등 처리하면 "at-least-once" 전송을 안전하게 흡수할 수 있다.
 *
 * @property aggregateId Kafka 파티션 키. 동일 애그리거트 이벤트의 순서를 보장한다.
 * @property payload 직렬화된 이벤트(JSON).
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
    ],
)
class OutboxEvent(

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    var eventId: UUID,

    @Column(name = "aggregate_type", nullable = false, length = 32, updatable = false)
    var aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 64, updatable = false)
    var aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    var eventType: String,

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: OutboxStatus,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "sent_at")
    var sentAt: Instant?,
) {
    /** 발행 성공 처리. */
    fun markSent(now: Instant) {
        this.status = OutboxStatus.SENT
        this.sentAt = now
    }

    /** 발행 실패 처리. 재시도 한계를 넘으면 FAILED 로 종결한다. */
    fun markFailedAttempt(maxRetry: Int) {
        this.retryCount += 1
        if (this.retryCount >= maxRetry) {
            this.status = OutboxStatus.FAILED
        }
    }

    companion object {
        /** 신규 PENDING 이벤트 생성. */
        fun pending(
            eventId: UUID,
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: String,
            now: Instant,
        ): OutboxEvent = OutboxEvent(
            eventId = eventId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            status = OutboxStatus.PENDING,
            retryCount = 0,
            createdAt = now,
            sentAt = null,
        )
    }
}
