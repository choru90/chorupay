package com.chorupay.infrastructure.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Redisson(분산락) 설정.
 *
 * 단일 노드 구성을 기본으로 한다. 운영에서는 Sentinel/Cluster 모드로 확장한다.
 * 분산락의 신뢰성(락 만료/페일오버)을 위해 Redis 고가용성 구성이 권장된다.
 */
@Configuration
class RedissonConfig(
    @Value("\${chorupay.redis.host:localhost}") private val host: String,
    @Value("\${chorupay.redis.port:6379}") private val port: Int,
    @Value("\${chorupay.redis.password:}") private val password: String,
    @Value("\${chorupay.redis.database:0}") private val database: Int,
) {

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSingleServer().apply {
            address = "redis://$host:$port"
            this.database = this@RedissonConfig.database
            // 비밀번호가 비어있지 않을 때만 설정한다.
            if (password.isNotBlank()) {
                this.password = this@RedissonConfig.password
            }
            // 커넥션 풀 - 고동시성 대비 여유 있게 구성
            connectionMinimumIdleSize = 8
            connectionPoolSize = 32
            // 락 대기 시 응답성을 위한 타임아웃
            timeout = 3000
            retryAttempts = 3
        }
        return Redisson.create(config)
    }
}
