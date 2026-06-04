package com.chorupay.core.domain.wallet

import com.chorupay.core.domain.exception.BalanceInconsistencyException
import com.chorupay.core.domain.exception.InsufficientBalanceException
import com.chorupay.core.domain.exception.InvalidAmountException
import com.chorupay.core.domain.ledger.EntryDirection
import com.chorupay.core.domain.ledger.LedgerEntry
import java.time.Instant
import java.util.UUID

/**
 * 충전/차감 연산의 결과.
 *
 * 도메인 메서드는 부수효과(저장)를 직접 일으키지 않는다. 대신 변경된 지갑 상태가 반영된 자기 자신과,
 * 그 변경을 설명하는 복식부기 원장 기입을 함께 반환한다. 애플리케이션 서비스가 이 둘을 원자적으로
 * 영속화한다. (도메인의 순수성 유지)
 *
 * @property wallet 연산 후 잔액이 갱신된 지갑
 * @property ledgerEntry 이 연산을 기록한 원장 기입
 */
data class WalletMutationResult(
    val wallet: Wallet,
    val ledgerEntry: LedgerEntry,
)

/**
 * Wallet 애그리거트 루트 - 사용자의 초루머니 지갑.
 *
 * [핵심 책임]
 *  - 잔액(balance) 에 대한 모든 변경의 단일 진입점. 외부에서 balance 를 직접 수정할 수 없다.
 *  - 충전(charge)/차감(withdraw) 시 도메인 불변식(음수 잔액 금지, 잔액 부족 검증)을 강제한다.
 *  - 모든 잔액 변경은 복식부기 원장 기입을 동반한다.
 *
 * [동시성 정합성 전략 - 2중 방어]
 *  1) 1차 방어: 애플리케이션 계층의 Redis 분산락(@DistributedLock)으로 동일 지갑에 대한
 *     쓰기를 직렬화한다. → 다중 인스턴스/다중 스레드 환경에서의 경합 제거.
 *  2) 2차 방어: [version] 필드를 통한 낙관적 락(Optimistic Lock). 분산락 우회/락 만료 등의
 *     극단적 상황에서도 DB 레벨에서 Lost Update 를 탐지한다.
 *  추가로 [validateConsistency] 로 원장 합계와 잔액의 일치를 상시 검증할 수 있다.
 *
 * @property id 지갑 식별자
 * @property userId 소유자(사용자) 식별자
 * @property balance 현재 초루머니 잔액 (0 이상 불변식)
 * @property version 낙관적 락 버전
 * @property createdAt 생성 시각
 * @property updatedAt 마지막 변경 시각
 */
class Wallet(
    val id: UUID,
    val userId: UUID,
    balance: Money,
    val version: Long,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var balance: Money = balance
        private set

    var updatedAt: Instant = updatedAt
        private set

    /**
     * 지갑을 충전한다. (CREDIT)
     *
     * @param amount 충전 금액 (0 초과)
     * @param transactionId 비즈니스 트랜잭션 식별자
     * @param now 현재 시각 (테스트 가능성을 위해 주입)
     * @param counterpartyAccount 상대 계정 (기본: 시스템 충전 계정)
     * @return 갱신된 지갑과 CREDIT 원장 기입
     * @throws InvalidAmountException 충전 금액이 0 이하인 경우
     */
    fun charge(
        amount: Money,
        transactionId: UUID,
        now: Instant,
        counterpartyAccount: String = "SYSTEM_CHARGE",
        description: String = "초루머니 충전",
    ): WalletMutationResult {
        if (!amount.isPositive()) {
            throw InvalidAmountException("충전 금액은 0보다 커야 합니다. amount=$amount")
        }

        val newBalance = this.balance + amount
        this.balance = newBalance
        this.updatedAt = now

        val entry = LedgerEntry.credit(
            walletId = id,
            transactionId = transactionId,
            amount = amount,
            balanceAfter = newBalance,
            counterpartyAccount = counterpartyAccount,
            description = description,
            occurredAt = now,
        )
        return WalletMutationResult(this, entry)
    }

    /**
     * 지갑에서 차감한다. (DEBIT) - 결제/출금 시 사용.
     *
     * 잔액 부족 시 [InsufficientBalanceException] 을 던져 즉시 실패(Fast-Fail) 시킨다.
     *
     * @param amount 차감 금액 (0 초과)
     * @param transactionId 비즈니스 트랜잭션 식별자
     * @param now 현재 시각
     * @param counterpartyAccount 상대 계정 (기본: PG 정산 계정)
     * @return 갱신된 지갑과 DEBIT 원장 기입
     * @throws InvalidAmountException 차감 금액이 0 이하인 경우
     * @throws InsufficientBalanceException 잔액이 부족한 경우
     */
    fun withdraw(
        amount: Money,
        transactionId: UUID,
        now: Instant,
        counterpartyAccount: String = "PG_SETTLEMENT",
        description: String = "결제 차감",
    ): WalletMutationResult {
        if (!amount.isPositive()) {
            throw InvalidAmountException("차감 금액은 0보다 커야 합니다. amount=$amount")
        }
        if (!canWithdraw(amount)) {
            throw InsufficientBalanceException(
                walletId = id.toString(),
                balance = balance.amount,
                requested = amount.amount,
            )
        }

        val newBalance = this.balance - amount
        this.balance = newBalance
        this.updatedAt = now

        val entry = LedgerEntry.debit(
            walletId = id,
            transactionId = transactionId,
            amount = amount,
            balanceAfter = newBalance,
            counterpartyAccount = counterpartyAccount,
            description = description,
            occurredAt = now,
        )
        return WalletMutationResult(this, entry)
    }

    /** [amount] 만큼 차감 가능한 잔액이 있는지 검사한다. (부수효과 없음) */
    fun canWithdraw(amount: Money): Boolean = balance.isGreaterThanOrEqual(amount)

    /**
     * 원장 기입 전체와 현재 잔액의 정합성을 검증한다.
     *
     * 복식부기 불변식: Σ(CREDIT) - Σ(DEBIT) == balance
     * 대규모 동시성 처리 후 배치/모니터링에서 호출하여 Lost Update / 이중 기입 등을 탐지한다.
     *
     * @param ledgerEntries 이 지갑에 속한 모든 원장 기입
     * @throws BalanceInconsistencyException 합계와 잔액이 불일치하는 경우
     */
    fun validateConsistency(ledgerEntries: List<LedgerEntry>) {
        val ledgerSum = ledgerEntries
            .filter { it.walletId == id }
            .sumOf { it.signedAmount() }

        if (ledgerSum != balance.amount) {
            throw BalanceInconsistencyException(
                walletId = id.toString(),
                walletBalance = balance.amount,
                ledgerBalance = ledgerSum,
            )
        }
    }

    /**
     * 정합성이 깨지지 않았는지 boolean 으로 빠르게 확인한다. (예외 없이 모니터링 지표 수집용)
     */
    fun isConsistentWith(ledgerEntries: List<LedgerEntry>): Boolean {
        val ledgerSum = ledgerEntries
            .filter { it.walletId == id }
            .sumOf { it.signedAmount() }
        return ledgerSum == balance.amount
    }

    companion object {
        /** 신규 지갑을 잔액 0원으로 생성한다. */
        fun create(userId: UUID, now: Instant): Wallet = Wallet(
            id = UUID.randomUUID(),
            userId = userId,
            balance = Money.ZERO,
            version = 0L,
            createdAt = now,
            updatedAt = now,
        )

        /** 영속 계층에서 복원할 때 사용하는 재구성(reconstitution) 팩토리. */
        fun reconstitute(
            id: UUID,
            userId: UUID,
            balance: Money,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant,
        ): Wallet = Wallet(id, userId, balance, version, createdAt, updatedAt)
    }
}
