package com.chorupay.infrastructure.persistence.payment

import com.chorupay.core.application.port.out.PaymentRepository
import com.chorupay.core.domain.payment.Payment
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 결제 아웃바운드 포트의 JPA 어댑터.
 */
@Component
class PaymentPersistenceAdapter(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun findById(paymentId: UUID): Payment? =
        paymentJpaRepository.findById(paymentId).orElse(null)?.toDomain()

    override fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentJpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()

    override fun save(payment: Payment): Payment {
        val existing = paymentJpaRepository.findById(payment.id).orElse(null)
        val entity = if (existing == null) {
            PaymentJpaEntity.fromDomain(payment)
        } else {
            // 상태/실패사유/시각만 갱신 (불변 필드는 유지)
            existing.status = payment.status
            existing.failureReason = payment.failureReason
            existing.updatedAt = payment.updatedAt
            existing
        }
        return paymentJpaRepository.save(entity).toDomain()
    }
}
