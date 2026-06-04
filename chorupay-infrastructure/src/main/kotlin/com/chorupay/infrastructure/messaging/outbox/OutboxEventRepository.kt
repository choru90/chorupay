package com.chorupay.infrastructure.messaging.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA 리포지토리 (Outbox).
 */
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {

    /**
     * 발행 대기(PENDING) 이벤트를 오래된 순으로 배치 조회한다.
     * [Pageable] 로 한 번에 처리할 양을 제한하여 스케줄러 폭주를 막는다.
     */
    fun findByStatusOrderByCreatedAtAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEvent>
}
