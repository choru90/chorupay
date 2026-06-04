package com.chorupay.api.dto

import com.chorupay.core.application.port.`in`.ChargeWalletCommand
import com.chorupay.core.application.port.`in`.ChargeWalletResult
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * 지갑 충전 요청 DTO.
 *
 * Bean Validation 으로 컨트롤러 진입 즉시 검증하여, 잘못된 요청을 도메인까지 보내지 않고
 * Fast-Fail 시킨다.
 *
 * @property amount 충전 금액 (1원 이상, 1회 1천만원 이하)
 * @property idempotencyKey 멱등성 키 (중복 충전 방지). 클라이언트가 생성하여 재시도 간 동일하게 전달.
 */
data class ChargeRequest(
    @field:NotNull(message = "충전 금액은 필수입니다.")
    @field:Min(value = 1, message = "충전 금액은 1원 이상이어야 합니다.")
    @field:Max(value = 10_000_000, message = "1회 충전 한도는 1천만원입니다.")
    val amount: Long,

    @field:NotBlank(message = "멱등성 키는 필수입니다.")
    val idempotencyKey: String,
) {
    /** 유스케이스 커맨드로 변환. */
    fun toCommand(userId: UUID): ChargeWalletCommand = ChargeWalletCommand(
        userId = userId,
        amount = amount,
        idempotencyKey = idempotencyKey,
    )
}

/**
 * 지갑 충전 응답 DTO.
 */
data class ChargeResponse(
    val walletId: UUID,
    val userId: UUID,
    val chargedAmount: Long,
    val balance: Long,
    val transactionId: UUID,
) {
    companion object {
        fun from(result: ChargeWalletResult): ChargeResponse = ChargeResponse(
            walletId = result.walletId,
            userId = result.userId,
            chargedAmount = result.chargedAmount,
            balance = result.balance,
            transactionId = result.transactionId,
        )
    }
}
