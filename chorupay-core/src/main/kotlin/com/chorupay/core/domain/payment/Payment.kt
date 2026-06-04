package com.chorupay.core.domain.payment

import com.chorupay.core.domain.exception.IllegalPaymentStateTransitionException
import com.chorupay.core.domain.exception.InvalidAmountException
import com.chorupay.core.domain.wallet.Money
import java.time.Instant
import java.util.UUID

/**
 * Payment 애그리거트 루트 - 단일 결제 건.
 *
 * 결제는 [PaymentStatus] 상태 머신을 통해서만 상태가 바뀐다. 모든 전이 메서드는
 * 허용되지 않은 전이 시 [IllegalPaymentStateTransitionException] 을 던져 불변식을 보호한다.
 *
 * [멱등성/중복결제 방지]
 *  - [idempotencyKey] 로 동일 요청의 중복 처리를 막는다. (클라이언트 재시도 안전)
 *  - [orderId] 는 주문 단위 식별자다.
 *
 * @property id 결제 식별자
 * @property walletId 결제에 사용된 지갑
 * @property userId 결제자
 * @property orderId 주문 식별자
 * @property amount 결제 금액
 * @property status 현재 결제 상태
 * @property idempotencyKey 멱등성 키 (중복 요청 차단)
 * @property failureReason 실패 사유 (FAIL 시)
 * @property createdAt 생성 시각
 * @property updatedAt 마지막 변경 시각
 */
class Payment(
    val id: UUID,
    val walletId: UUID,
    val userId: UUID,
    val orderId: String,
    val amount: Money,
    status: PaymentStatus,
    val idempotencyKey: String,
    failureReason: String?,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var status: PaymentStatus = status
        private set

    var failureReason: String? = failureReason
        private set

    var updatedAt: Instant = updatedAt
        private set

    init {
        if (!amount.isPositive()) {
            throw InvalidAmountException("결제 금액은 0보다 커야 합니다. amount=$amount")
        }
    }

    /**
     * 결제를 성공 처리한다. (READY -> SUCCESS)
     * @throws IllegalPaymentStateTransitionException 현재 상태에서 SUCCESS 로 전이할 수 없는 경우
     */
    fun markSuccess(now: Instant) {
        transitionTo(PaymentStatus.SUCCESS, now)
        this.failureReason = null
    }

    /**
     * 결제를 실패 처리한다. (READY -> FAIL)
     * @param reason 실패 사유
     * @throws IllegalPaymentStateTransitionException FAIL 로 전이할 수 없는 경우
     */
    fun markFail(reason: String, now: Instant) {
        transitionTo(PaymentStatus.FAIL, now)
        this.failureReason = reason
    }

    /**
     * 성공한 결제를 취소(환불)한다. (SUCCESS -> CANCELED)
     * @throws IllegalPaymentStateTransitionException SUCCESS 상태가 아닌 경우
     */
    fun cancel(now: Instant) {
        transitionTo(PaymentStatus.CANCELED, now)
    }

    /** 상태 머신 전이의 단일 통로. 허용 여부를 검사한 뒤에만 상태를 바꾼다. */
    private fun transitionTo(target: PaymentStatus, now: Instant) {
        if (!status.canTransitionTo(target)) {
            throw IllegalPaymentStateTransitionException(
                from = status.name,
                to = target.name,
            )
        }
        this.status = target
        this.updatedAt = now
    }

    /** 결제가 종료 상태(FAIL/CANCELED, 또는 더 이상 전이 불가)인지 여부. */
    fun isFinalized(): Boolean = status.isTerminal()

    companion object {
        /** 신규 결제를 READY 상태로 생성한다. */
        fun create(
            walletId: UUID,
            userId: UUID,
            orderId: String,
            amount: Money,
            idempotencyKey: String,
            now: Instant,
        ): Payment = Payment(
            id = UUID.randomUUID(),
            walletId = walletId,
            userId = userId,
            orderId = orderId,
            amount = amount,
            status = PaymentStatus.READY,
            idempotencyKey = idempotencyKey,
            failureReason = null,
            createdAt = now,
            updatedAt = now,
        )

        /** 영속 계층에서 복원할 때 사용하는 재구성 팩토리. */
        fun reconstitute(
            id: UUID,
            walletId: UUID,
            userId: UUID,
            orderId: String,
            amount: Money,
            status: PaymentStatus,
            idempotencyKey: String,
            failureReason: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): Payment = Payment(
            id, walletId, userId, orderId, amount, status,
            idempotencyKey, failureReason, createdAt, updatedAt,
        )
    }
}
