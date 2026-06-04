package com.chorupay.infrastructure.persistence.ledger

import com.chorupay.core.domain.ledger.EntryDirection
import com.chorupay.core.domain.ledger.LedgerEntry
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
 * 원장 기입 JPA 엔티티 (추가 전용 / append-only).
 *
 * [인덱스 설계]
 *  - (wallet_id) : 지갑별 원장 조회/정합성 검증.
 *  - (transaction_id) : 멱등성 확인(existsByTransactionId)의 빠른 조회.
 */
@Entity
@Table(
    name = "ledger_entries",
    indexes = [
        Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
        Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
    ],
)
class LedgerEntryJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "wallet_id", nullable = false, updatable = false)
    var walletId: UUID,

    @Column(name = "transaction_id", nullable = false, updatable = false)
    var transactionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16, updatable = false)
    var direction: EntryDirection,

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: Long,

    @Column(name = "balance_after", nullable = false, updatable = false)
    var balanceAfter: Long,

    @Column(name = "counterparty_account", nullable = false, length = 64, updatable = false)
    var counterpartyAccount: String,

    @Column(name = "description", nullable = false, length = 255, updatable = false)
    var description: String,

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,
) {
    fun toDomain(): LedgerEntry = LedgerEntry(
        id = id,
        walletId = walletId,
        transactionId = transactionId,
        direction = direction,
        amount = Money.of(amount),
        balanceAfter = Money.of(balanceAfter),
        counterpartyAccount = counterpartyAccount,
        description = description,
        occurredAt = occurredAt,
    )

    companion object {
        fun fromDomain(entry: LedgerEntry): LedgerEntryJpaEntity = LedgerEntryJpaEntity(
            id = entry.id,
            walletId = entry.walletId,
            transactionId = entry.transactionId,
            direction = entry.direction,
            amount = entry.amount.amount,
            balanceAfter = entry.balanceAfter.amount,
            counterpartyAccount = entry.counterpartyAccount,
            description = entry.description,
            occurredAt = entry.occurredAt,
        )
    }
}
