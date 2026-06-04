package com.chorupay.infrastructure.persistence.wallet

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA 리포지토리 (지갑).
 *
 * 순수 영속성 인터페이스로, core 의 [com.chorupay.core.application.port.out.WalletRepository]
 * 포트와는 분리되어 있다. 포트 구현은 [WalletPersistenceAdapter] 가 담당한다.
 */
interface WalletJpaRepository : JpaRepository<WalletJpaEntity, UUID> {

    /** 사용자 식별자로 지갑을 조회한다. */
    fun findByUserId(userId: UUID): WalletJpaEntity?
}
