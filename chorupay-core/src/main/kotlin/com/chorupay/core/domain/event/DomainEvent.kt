package com.chorupay.core.domain.event

import java.time.Instant
import java.util.UUID

/**
 * 도메인 이벤트의 공통 계약.
 *
 * 도메인에서 발생한 의미 있는 사실(충전됨, 결제됨)을 표현한다. 이 이벤트들은 Transactional Outbox
 * 패턴을 통해 비즈니스 트랜잭션과 "원자적으로" 저장된 뒤, 별도 발행기에 의해 Kafka 로 전파된다.
 *
 * @property eventId 이벤트 고유 식별자 (멱등 소비를 위한 키)
 * @property aggregateType 이벤트를 발생시킨 애그리거트 종류 (예: "WALLET", "PAYMENT")
 * @property aggregateId 애그리거트 식별자 (Kafka 파티션 키로도 사용 → 동일 애그리거트 순서 보장)
 * @property eventType 이벤트 타입 명 (예: "WalletCharged")
 * @property occurredAt 이벤트 발생 시각
 */
interface DomainEvent {
    val eventId: UUID
    val aggregateType: String
    val aggregateId: String
    val eventType: String
    val occurredAt: Instant
}

/**
 * 지갑 충전 완료 이벤트.
 */
data class WalletChargedEvent(
    override val eventId: UUID,
    override val aggregateId: String,
    val walletId: UUID,
    val userId: UUID,
    val transactionId: UUID,
    val chargedAmount: Long,
    val balanceAfter: Long,
    override val occurredAt: Instant,
) : DomainEvent {
    override val aggregateType: String = "WALLET"
    override val eventType: String = "WalletCharged"
}

/**
 * 결제 처리 완료 이벤트. (성공/실패 모두 발행하여 후속 정산/알림이 구독)
 */
data class PaymentProcessedEvent(
    override val eventId: UUID,
    override val aggregateId: String,
    val paymentId: UUID,
    val walletId: UUID,
    val userId: UUID,
    val orderId: String,
    val amount: Long,
    val status: String,
    val balanceAfter: Long?,
    override val occurredAt: Instant,
) : DomainEvent {
    override val aggregateType: String = "PAYMENT"
    override val eventType: String = "PaymentProcessed"
}
