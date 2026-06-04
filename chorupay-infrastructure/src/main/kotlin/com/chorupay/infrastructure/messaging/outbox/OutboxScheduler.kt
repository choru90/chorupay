package com.chorupay.infrastructure.messaging.outbox

import com.chorupay.infrastructure.messaging.kafka.KafkaEventProducer
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Outbox 발행 스케줄러 (Polling Publisher).
 *
 * 주기적으로 PENDING 이벤트를 조회하여 Kafka 로 발행하고, 성공 시 SENT 로 마킹한다.
 * 발행 실패는 retryCount 를 올리며, 한계를 넘으면 FAILED 로 격리한다.
 *
 * [전송 보장] at-least-once. (발행 후 SENT 마킹 사이 장애 시 재발행될 수 있으나,
 *  소비자가 eventId 로 멱등 처리하면 안전하다)
 *
 * 실제 운영에서는 분산 환경 중복 폴링을 막기 위해 SELECT ... FOR UPDATE SKIP LOCKED 또는
 * 샤딩/리더선출을 적용한다. 본 스캐폴딩은 동작 가능한 뼈대를 제공한다.
 */
@Component
class OutboxScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaEventProducer: KafkaEventProducer,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 한 번에 처리할 이벤트 수. */
        private const val BATCH_SIZE = 100

        /** 최대 재시도 횟수. 초과 시 FAILED 격리. */
        private const val MAX_RETRY = 5
    }

    /**
     * 고정 지연(1초)으로 PENDING 이벤트를 발행한다.
     *
     * 각 배치를 하나의 트랜잭션으로 처리하여 상태 변경(SENT/retry)을 원자적으로 반영한다.
     * Kafka 발행 자체는 외부 I/O 이므로, future.join() 으로 결과를 확인한 뒤 상태를 확정한다.
     */
    @Scheduled(fixedDelayString = "\${chorupay.outbox.poll-interval-ms:1000}")
    @Transactional
    fun publishPendingEvents() {
        val pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
            status = OutboxStatus.PENDING,
            pageable = PageRequest.of(0, BATCH_SIZE),
        )
        if (pending.isEmpty()) return

        val now = clock.instant()
        var success = 0
        var failed = 0

        for (event in pending) {
            try {
                // 동기적으로 결과를 기다려 상태 확정 (배치 트랜잭션 일관성)
                kafkaEventProducer.send(event).join()
                event.markSent(now)
                success++
            } catch (e: Exception) {
                event.markFailedAttempt(MAX_RETRY)
                failed++
                log.warn(
                    "Outbox 이벤트 발행 실패. eventId={}, retryCount={}, status={}",
                    event.eventId, event.retryCount, event.status, e,
                )
            }
        }
        // 변경된 엔티티는 트랜잭션 커밋 시 더티 체킹으로 반영된다.
        if (success > 0 || failed > 0) {
            log.info("Outbox 발행 배치 완료. success={}, failed={}", success, failed)
        }
    }
}
