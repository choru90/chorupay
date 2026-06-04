package com.chorupay.infrastructure.persistence.payment

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA 리포지토리 (결제).
 */
interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, UUID> {

    /** 멱등성 키로 결제 조회 (중복 결제 차단). */
    fun findByIdempotencyKey(idempotencyKey: String): PaymentJpaEntity?
}
