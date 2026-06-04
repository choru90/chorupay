package com.chorupay.core.domain.ledger

import com.chorupay.core.domain.exception.InvalidAmountException
import com.chorupay.core.domain.wallet.Money
import java.time.Instant
import java.util.UUID

/**
 * 원장 기입 방향(복식부기).
 *
 * 사용자 지갑 관점에서:
 * - [CREDIT] : 지갑 잔액이 "증가" 하는 기입 (충전, 결제 취소 환불 등)
 * - [DEBIT]  : 지갑 잔액이 "감소" 하는 기입 (결제 차감, 출금 등)
 *
 * 복식부기 원칙상 모든 자금 이동은 한쪽 계정의 DEBIT 과 반대쪽 계정의 CREDIT 으로 균형을 이룬다.
 * 본 시스템은 사용자 지갑 계정 라인을 [LedgerEntry] 로 기록하며, 상대 계정(시스템/PG 정산 계정)은
 * [counterpartyAccount] 메타데이터로 식별한다.
 */
enum class EntryDirection {
    CREDIT,
    DEBIT,
}

/**
 * 원장 기입(LedgerEntry) - 복식부기 원칙에 따른 단일 거래 라인.
 *
 * 불변(immutable) 이며 "추가 전용(append-only)" 이다. 한 번 기록된 원장은 수정/삭제되지 않으며
 * 정정이 필요하면 반대 방향의 새 기입(역분개)을 추가한다. 이는 금융 거래의 감사 추적성을 보장한다.
 *
 * 잔액 정합성 검증의 기준값:
 *   특정 지갑의 모든 원장 기입에 대해 (CREDIT 합계 - DEBIT 합계) 는 항상 현재 지갑 잔액과 같아야 한다.
 *
 * @property id 원장 식별자
 * @property walletId 대상 지갑 식별자
 * @property transactionId 동일 비즈니스 트랜잭션을 묶는 식별자(충전/결제 1건 = 1 transactionId)
 * @property direction 기입 방향 (CREDIT/DEBIT)
 * @property amount 기입 금액 (항상 양수)
 * @property balanceAfter 이 기입 직후의 지갑 잔액 스냅샷 (정합성 빠른 검증/감사에 사용)
 * @property counterpartyAccount 상대 계정 식별자 (예: "SYSTEM_CHARGE", "PG_SETTLEMENT")
 * @property description 사람이 읽을 수 있는 설명
 * @property occurredAt 기입 발생 시각
 */
data class LedgerEntry(
    val id: UUID,
    val walletId: UUID,
    val transactionId: UUID,
    val direction: EntryDirection,
    val amount: Money,
    val balanceAfter: Money,
    val counterpartyAccount: String,
    val description: String,
    val occurredAt: Instant,
) {
    init {
        // 원장 기입 금액은 0원일 수 없다. (의미 없는 기입 방지)
        if (!amount.isPositive()) {
            throw InvalidAmountException("원장 기입 금액은 0보다 커야 합니다. amount=$amount")
        }
    }

    /** 잔액에 미치는 부호 있는 영향. CREDIT 은 +, DEBIT 은 - 로 환산한다. */
    fun signedAmount(): Long = when (direction) {
        EntryDirection.CREDIT -> amount.amount
        EntryDirection.DEBIT -> -amount.amount
    }

    companion object {
        /** 잔액 증가(CREDIT) 기입 생성 */
        fun credit(
            walletId: UUID,
            transactionId: UUID,
            amount: Money,
            balanceAfter: Money,
            counterpartyAccount: String,
            description: String,
            occurredAt: Instant,
        ): LedgerEntry = LedgerEntry(
            id = UUID.randomUUID(),
            walletId = walletId,
            transactionId = transactionId,
            direction = EntryDirection.CREDIT,
            amount = amount,
            balanceAfter = balanceAfter,
            counterpartyAccount = counterpartyAccount,
            description = description,
            occurredAt = occurredAt,
        )

        /** 잔액 감소(DEBIT) 기입 생성 */
        fun debit(
            walletId: UUID,
            transactionId: UUID,
            amount: Money,
            balanceAfter: Money,
            counterpartyAccount: String,
            description: String,
            occurredAt: Instant,
        ): LedgerEntry = LedgerEntry(
            id = UUID.randomUUID(),
            walletId = walletId,
            transactionId = transactionId,
            direction = EntryDirection.DEBIT,
            amount = amount,
            balanceAfter = balanceAfter,
            counterpartyAccount = counterpartyAccount,
            description = description,
            occurredAt = occurredAt,
        )
    }
}
