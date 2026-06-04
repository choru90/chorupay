package com.chorupay.infrastructure.persistence.wallet

import com.chorupay.core.application.port.out.WalletRepository
import com.chorupay.core.domain.wallet.Wallet
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 지갑 아웃바운드 포트의 JPA 어댑터.
 *
 * core 의 [WalletRepository] 포트를 구현하여 도메인과 JPA 를 잇는다.
 * 갱신 시에는 기존 영속 엔티티를 로드해 필드만 수정 → @Version 낙관적 락이 정상 동작하도록 한다.
 */
@Component
class WalletPersistenceAdapter(
    private val walletJpaRepository: WalletJpaRepository,
) : WalletRepository {

    override fun findById(walletId: UUID): Wallet? =
        walletJpaRepository.findById(walletId).orElse(null)?.toDomain()

    override fun findByUserId(userId: UUID): Wallet? =
        walletJpaRepository.findByUserId(userId)?.toDomain()

    override fun save(wallet: Wallet): Wallet {
        val existing = walletJpaRepository.findById(wallet.id).orElse(null)
        val entity = if (existing == null) {
            // 신규 지갑
            WalletJpaEntity.fromDomain(wallet)
        } else {
            // 기존 지갑 갱신 - 변경 가능 필드만 반영, version 은 JPA 가 증가시킨다.
            existing.balance = wallet.balance.amount
            existing.updatedAt = wallet.updatedAt
            existing
        }
        return walletJpaRepository.save(entity).toDomain()
    }
}
