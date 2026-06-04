package com.chorupay.api.ratelimit

import com.chorupay.api.dto.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap

/**
 * 클라이언트(IP) 단위 Rate Limiting 필터.
 *
 * [대규모 트래픽 방어 - 가장 바깥 관문]
 *  컨트롤러/유스케이스에 도달하기 전에, 과도한 요청을 보내는 클라이언트를 즉시 429 로 차단한다.
 *  비싼 분산락/DB 접근까지 가지 않고 차단하므로 시스템 보호 효과가 크다. (Fast-Fail)
 *
 * 본 구현은 단일 인스턴스 인메모리 버킷이다. 다중 인스턴스 환경에서는 Redis 기반(예: Redisson
 * RRateLimiter)으로 전역 한도를 공유하도록 확장한다.
 *
 * @Order(1) 로 필터 체인의 앞단에 위치시킨다.
 */
@Component
@Order(1)
class RateLimitingFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${chorupay.rate-limit.capacity:50}") private val capacity: Double,
    @Value("\${chorupay.rate-limit.refill-per-second:25}") private val refillPerSecond: Double,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 클라이언트 식별자(IP) → 토큰 버킷. */
    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 상태 확인(actuator) 등은 제한 대상에서 제외
        if (!request.requestURI.startsWith("/api/")) {
            filterChain.doFilter(request, response)
            return
        }

        val clientKey = resolveClientKey(request)
        val bucket = buckets.computeIfAbsent(clientKey) {
            TokenBucket(capacity = capacity, refillTokensPerSecond = refillPerSecond)
        }

        if (bucket.tryConsume()) {
            filterChain.doFilter(request, response)
        } else {
            log.warn("Rate limit 초과로 차단. client={}, uri={}", clientKey, request.requestURI)
            writeTooManyRequests(response)
        }
    }

    /** 프록시 환경을 고려해 X-Forwarded-For 우선, 없으면 remoteAddr 사용. */
    private fun resolveClientKey(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) forwarded.split(",").first().trim()
        else request.remoteAddr ?: "unknown"
    }

    private fun writeTooManyRequests(response: HttpServletResponse) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ApiResponse.fail(
            code = "RATE_LIMITED",
            message = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
