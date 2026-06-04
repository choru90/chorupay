package com.chorupay.core.application.port.`in`

import java.util.UUID

/**
 * 지갑 충전 유스케이스 (인바운드 포트).
 *
 * 인바운드 어댑터(REST Controller)는 이 인터페이스에만 의존하고, 구현(WalletService)은 core 에 있다.
 */
interface ChargeWalletUseCase {

    /**
     * 지갑을 충전한다.
     * @return 충전 결과 (갱신된 잔액 등)
     */
    fun charge(command: ChargeWalletCommand): ChargeWalletResult
}

/**
 * 충전 커맨드.
 *
 * @property userId 충전 대상 사용자
 * @property amount 충전 금액 (원). 0 초과여야 한다.
 * @property idempotencyKey 멱등성 키. 동일 키 재요청 시 중복 충전을 막는다.
 */
data class ChargeWalletCommand(
    val userId: UUID,
    val amount: Long,
    val idempotencyKey: String,
)

/**
 * 충전 결과.
 *
 * @property walletId 지갑 식별자
 * @property userId 사용자 식별자
 * @property chargedAmount 이번에 충전된 금액
 * @property balance 충전 후 잔액
 * @property transactionId 이 충전을 식별하는 트랜잭션 ID
 */
data class ChargeWalletResult(
    val walletId: UUID,
    val userId: UUID,
    val chargedAmount: Long,
    val balance: Long,
    val transactionId: UUID,
)
