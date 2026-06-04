package com.chorupay.infrastructure.persistence.ledger

import com.chorupay.core.application.port.out.LedgerRepository
import com.chorupay.core.domain.ledger.LedgerEntry
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 원장 아웃바운드 포트의 JPA 어댑터.
 */
@Component
class LedgerPersistenceAdapter(
    private val ledgerJpaRepository: LedgerJpaRepository,
) : LedgerRepository {

    override fun append(entry: LedgerEntry): LedgerEntry =
        ledgerJpaRepository.save(LedgerEntryJpaEntity.fromDomain(entry)).toDomain()

    override fun appendAll(entries: List<LedgerEntry>): List<LedgerEntry> =
        ledgerJpaRepository
            .saveAll(entries.map { LedgerEntryJpaEntity.fromDomain(it) })
            .map { it.toDomain() }

    override fun findByWalletId(walletId: UUID): List<LedgerEntry> =
        ledgerJpaRepository.findByWalletIdOrderByOccurredAtAsc(walletId).map { it.toDomain() }

    override fun sumSignedAmountByWalletId(walletId: UUID): Long =
        ledgerJpaRepository.sumSignedAmountByWalletId(walletId)

    override fun existsByTransactionId(transactionId: UUID): Boolean =
        ledgerJpaRepository.existsByTransactionId(transactionId)
}
