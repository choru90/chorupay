package com.chorupay.core.application.port.`in`

import java.util.UUID

/**
 * 결제 처리 유스케이스 (인바운드 포트).
 */
interface ProcessPaymentUseCase {

    /**
     * 결제를 요청/처리한다. 잔액에서 차감하고 결제를 승인한다.
     * 잔액 부족 시 결제는 FAIL 로 종결되며 결과에 사유가 담긴다.
     */
    fun process(command: ProcessPaymentCommand): ProcessPaymentResult
}

/**
 * 결제 커맨드.
 *
 * @property userId 결제자
 * @property orderId 주문 식별자
 * @property amount 결제 금액 (원). 0 초과.
 * @property idempotencyKey 멱등성 키. 동일 키 재요청 시 기존 결제 결과를 반환한다.
 */
data class ProcessPaymentCommand(
    val userId: UUID,
    val orderId: String,
    val amount: Long,
    val idempotencyKey: String,
)

/**
 * 결제 결과.
 *
 * @property paymentId 결제 식별자
 * @property orderId 주문 식별자
 * @property status 결제 상태 (SUCCESS/FAIL 등)
 * @property amount 결제 금액
 * @property balanceAfter 결제 후 잔액 (실패 시 null 일 수 있음)
 * @property failureReason 실패 사유 (실패 시)
 */
data class ProcessPaymentResult(
    val paymentId: UUID,
    val orderId: String,
    val status: String,
    val amount: Long,
    val balanceAfter: Long?,
    val failureReason: String?,
)
