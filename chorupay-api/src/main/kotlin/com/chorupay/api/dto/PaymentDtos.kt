package com.chorupay.api.dto

import com.chorupay.core.application.port.`in`.ProcessPaymentCommand
import com.chorupay.core.application.port.`in`.ProcessPaymentResult
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * 결제 요청 DTO.
 *
 * @property userId 결제자 식별자
 * @property orderId 주문 식별자
 * @property amount 결제 금액 (1원 이상)
 * @property idempotencyKey 멱등성 키 (중복 결제 방지)
 */
data class PaymentRequest(
    @field:NotNull(message = "사용자 ID는 필수입니다.")
    val userId: UUID,

    @field:NotBlank(message = "주문 ID는 필수입니다.")
    @field:Size(max = 64, message = "주문 ID는 64자 이하여야 합니다.")
    val orderId: String,

    @field:NotNull(message = "결제 금액은 필수입니다.")
    @field:Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
    val amount: Long,

    @field:NotBlank(message = "멱등성 키는 필수입니다.")
    val idempotencyKey: String,
) {
    fun toCommand(): ProcessPaymentCommand = ProcessPaymentCommand(
        userId = userId,
        orderId = orderId,
        amount = amount,
        idempotencyKey = idempotencyKey,
    )
}

/**
 * 결제 응답 DTO.
 */
data class PaymentResponse(
    val paymentId: UUID,
    val orderId: String,
    val status: String,
    val amount: Long,
    val balanceAfter: Long?,
    val failureReason: String?,
) {
    companion object {
        fun from(result: ProcessPaymentResult): PaymentResponse = PaymentResponse(
            paymentId = result.paymentId,
            orderId = result.orderId,
            status = result.status,
            amount = result.amount,
            balanceAfter = result.balanceAfter,
            failureReason = result.failureReason,
        )
    }
}
