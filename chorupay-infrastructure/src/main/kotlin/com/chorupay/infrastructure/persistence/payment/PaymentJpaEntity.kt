package com.chorupay.infrastructure.persistence.payment

import com.chorupay.core.domain.payment.Payment
import com.chorupay.core.domain.payment.PaymentStatus
import com.chorupay.core.domain.wallet.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 결제 JPA 엔티티.
 *
 * [멱등성] idempotency_key 에 유니크 제약을 걸어 DB 레벨에서도 중복 결제를 차단한다.
 */
@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "uk_payment_idempotency_key", columnList = "idempotency_key", unique = true),
        Index(name = "idx_payment_wallet_id", columnList = "wallet_id"),
    ],
)
class PaymentJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "wallet_id", nullable = false, updatable = false)
    var walletId: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,

    @Column(name = "order_id", nullable = false, length = 64, updatable = false)
    var orderId: String,

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PaymentStatus,

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    var idempotencyKey: String,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String?,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    fun toDomain(): Payment = Payment.reconstitute(
        id = id,
        walletId = walletId,
        userId = userId,
        orderId = orderId,
        amount = Money.of(amount),
        status = status,
        idempotencyKey = idempotencyKey,
        failureReason = failureReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(payment: Payment): PaymentJpaEntity = PaymentJpaEntity(
            id = payment.id,
            walletId = payment.walletId,
            userId = payment.userId,
            orderId = payment.orderId,
            amount = payment.amount.amount,
            status = payment.status,
            idempotencyKey = payment.idempotencyKey,
            failureReason = payment.failureReason,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
        )
    }
}
