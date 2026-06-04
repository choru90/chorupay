package com.chorupay.infrastructure.messaging.outbox

import com.chorupay.core.application.port.out.EventPublisher
import com.chorupay.core.domain.event.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * [EventPublisher] 포트의 Outbox 구현.
 *
 * 도메인 이벤트를 Kafka 로 "직접" 보내지 않고, 현재 진행 중인 비즈니스 트랜잭션 안에서
 * [OutboxEvent] 테이블에 INSERT 한다. (이중 쓰기 문제 제거)
 *
 * 주의: 이 어댑터는 호출자의 트랜잭션에 참여해야 하므로 별도의 @Transactional 을 두지 않는다.
 *       (분산락 Aspect 가 연 REQUIRES_NEW 트랜잭션 경계 안에서 호출된다)
 */
@Component
class OutboxEventPublisherAdapter(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) : EventPublisher {

    override fun publish(event: DomainEvent) {
        val payload = objectMapper.writeValueAsString(event)
        val outbox = OutboxEvent.pending(
            eventId = event.eventId,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = payload,
            now = event.occurredAt,
        )
        outboxEventRepository.save(outbox)
    }

    override fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}
