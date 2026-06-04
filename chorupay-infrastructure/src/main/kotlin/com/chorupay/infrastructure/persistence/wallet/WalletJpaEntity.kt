package com.chorupay.infrastructure.persistence.wallet

import com.chorupay.core.domain.wallet.Money
import com.chorupay.core.domain.wallet.Wallet
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * 지갑 JPA 엔티티.
 *
 * 도메인 [Wallet] 과 분리된 영속성 전용 모델이다. (헥사고날 — 도메인은 JPA 를 모른다)
 *
 * [낙관적 락] [version] 에 @Version 을 두어 동시 갱신 시 Lost Update 를 DB 레벨에서 탐지한다.
 *  분산락이 1차 방어선이고 이 낙관적 락이 2차 방어선이다.
 */
@Entity
@Table(name = "wallets")
class WalletJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    var userId: UUID,

    @Column(name = "balance", nullable = false)
    var balance: Long,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    /** 영속 모델 → 도메인 모델 복원. */
    fun toDomain(): Wallet = Wallet.reconstitute(
        id = id,
        userId = userId,
        balance = Money.of(balance),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        /** 도메인 모델 → 신규 영속 엔티티. */
        fun fromDomain(wallet: Wallet): WalletJpaEntity = WalletJpaEntity(
            id = wallet.id,
            userId = wallet.userId,
            balance = wallet.balance.amount,
            version = wallet.version,
            createdAt = wallet.createdAt,
            updatedAt = wallet.updatedAt,
        )
    }
}
