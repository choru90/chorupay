package com.chorupay.infrastructure.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

/**
 * Kafka 프로듀서 설정.
 *
 * [신뢰성 우선 설정]
 *  - acks=all : 모든 ISR 복제 확인 후 성공 처리 (데이터 유실 최소화)
 *  - enable.idempotence=true : 프로듀서 재시도로 인한 중복 메시지 방지
 *  - retries 다수 : 일시적 장애에 대한 자동 재시도
 *  Outbox 가 at-least-once 를 보장하고, 이 설정이 브로커 단 중복/유실을 줄인다.
 */
@Configuration
class KafkaProducerConfig(
    @Value("\${chorupay.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String,
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val props = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1,
            ProducerConfig.LINGER_MS_CONFIG to 5,
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> = KafkaTemplate(producerFactory())
}
