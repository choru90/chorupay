package com.chorupay.infrastructure.persistence.ledger

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data JPA 리포지토리 (원장).
 */
interface LedgerJpaRepository : JpaRepository<LedgerEntryJpaEntity, UUID> {

    /** 지갑별 원장 전체 조회 (발생순). */
    fun findByWalletIdOrderByOccurredAtAsc(walletId: UUID): List<LedgerEntryJpaEntity>

    /** 트랜잭션 ID 존재 여부 (멱등성 검사). */
    fun existsByTransactionId(transactionId: UUID): Boolean

    /**
     * 지갑 원장의 부호 있는 합계를 DB 에서 직접 계산한다.
     * CREDIT 은 +, DEBIT 은 - 로 합산. 전체 행을 메모리에 적재하지 않아 대용량에 적합하다.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN l.direction = com.chorupay.core.domain.ledger.EntryDirection.CREDIT
                                 THEN l.amount ELSE -l.amount END), 0)
        FROM LedgerEntryJpaEntity l
        WHERE l.walletId = :walletId
        """,
    )
    fun sumSignedAmountByWalletId(@Param("walletId") walletId: UUID): Long
}
