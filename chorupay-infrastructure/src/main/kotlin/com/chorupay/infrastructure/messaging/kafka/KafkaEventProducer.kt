package com.chorupay.infrastructure.messaging.kafka

import com.chorupay.infrastructure.messaging.outbox.OutboxEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Kafka 이벤트 프로듀서.
 *
 * Outbox 에 적재된 이벤트를 실제 Kafka 토픽으로 발행한다.
 * - 토픽: aggregateType 을 소문자로 한 "chorupay.{aggregateType}.events"
 * - 파티션 키: aggregateId → 동일 애그리거트 이벤트의 순서 보장
 *
 * 발행은 비동기이며, 호출자(스케줄러)는 반환된 future 로 성공/실패를 판정한다.
 */
@Component
class KafkaEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    companion object {
        private const val TOPIC_PREFIX = "chorupay."
        private const val TOPIC_SUFFIX = ".events"
    }

    /**
     * Outbox 이벤트를 Kafka 로 발행하고, 전송 완료 future 를 반환한다.
     */
    fun send(event: OutboxEvent): CompletableFuture<Void> {
        val topic = TOPIC_PREFIX + event.aggregateType.lowercase() + TOPIC_SUFFIX
        // key = aggregateId (파티션 라우팅), value = JSON payload
        return kafkaTemplate.send(topic, event.aggregateId, event.payload)
            .thenApply<Void> { /* SendResult -> Void */ null }
    }
}
