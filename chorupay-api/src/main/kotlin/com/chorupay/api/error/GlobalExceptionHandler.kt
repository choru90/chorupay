package com.chorupay.api.error

import com.chorupay.api.dto.ApiResponse
import com.chorupay.core.domain.exception.BalanceInconsistencyException
import com.chorupay.core.domain.exception.DomainException
import com.chorupay.core.domain.exception.EntityNotFoundException
import com.chorupay.core.domain.exception.IllegalPaymentStateTransitionException
import com.chorupay.core.domain.exception.InsufficientBalanceException
import com.chorupay.core.domain.exception.InvalidAmountException
import com.chorupay.infrastructure.lock.DistributedLockAcquisitionException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 핸들러.
 *
 * 도메인/인프라/검증 예외를 일관된 [ApiResponse] 와 적절한 HTTP 상태 코드로 변환한다.
 *
 * [대규모 트래픽 대응 매핑 요지]
 *  - 분산락 획득 실패 / Rate Limit 초과 → 429 (즉시 실패, 재시도 유도)
 *  - 잔액 부족 / 검증 실패 → 400 또는 409 (클라이언트 측 교정 가능)
 *  - 잔액 정합성 위반 → 500 + 경보 (절대 발생하면 안 되는 시스템 신호)
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 잔액 부족 → 409 Conflict */
    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(e: InsufficientBalanceException): ResponseEntity<ApiResponse<Nothing>> {
        log.info("잔액 부족: {}", e.message)
        return build(HttpStatus.CONFLICT, e.errorCode, e.message)
    }

    /** 금액/상태 전이 등 도메인 규칙 위반 → 400 Bad Request */
    @ExceptionHandler(InvalidAmountException::class, IllegalPaymentStateTransitionException::class)
    fun handleBadDomainRequest(e: DomainException): ResponseEntity<ApiResponse<Nothing>> {
        log.info("도메인 규칙 위반: {}", e.message)
        return build(HttpStatus.BAD_REQUEST, e.errorCode, e.message)
    }

    /** 엔티티 미존재 → 404 Not Found */
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(e: EntityNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        log.info("엔티티 미존재: {}", e.message)
        return build(HttpStatus.NOT_FOUND, e.errorCode, e.message)
    }

    /**
     * 잔액 정합성 위반 → 500. 절대 일어나선 안 되는 신호이므로 ERROR 로 기록하고 경보 대상이다.
     */
    @ExceptionHandler(BalanceInconsistencyException::class)
    fun handleInconsistency(e: BalanceInconsistencyException): ResponseEntity<ApiResponse<Nothing>> {
        log.error("[ALERT] 잔액 정합성 위반: {}", e.message)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.errorCode, "시스템 정합성 오류가 발생했습니다.")
    }

    /** 그 외 도메인 예외 → 400 (방어적 일괄 처리) */
    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("처리되지 않은 도메인 예외: {}", e.message)
        return build(HttpStatus.BAD_REQUEST, e.errorCode, e.message)
    }

    /**
     * 분산락 획득 실패 → 429 Too Many Requests.
     * 경합이 심해 제한 시간 내 락을 못 얻은 경우. 클라이언트에 재시도(backoff) 를 유도한다.
     */
    @ExceptionHandler(DistributedLockAcquisitionException::class)
    fun handleLockFailure(e: DistributedLockAcquisitionException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("분산락 경합으로 Fast-Fail: {}", e.lockKey)
        return build(
            HttpStatus.TOO_MANY_REQUESTS,
            "LOCK_ACQUISITION_FAILED",
            "요청이 일시적으로 많습니다. 잠시 후 다시 시도해 주세요.",
        )
    }

    /** Bean Validation 실패 → 400. 필드별 메시지를 합쳐 반환한다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = e.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        log.info("요청 검증 실패: {}", message)
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message)
    }

    /** 최후의 방어선 → 500. 예기치 못한 모든 예외를 흡수한다. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("예기치 못한 오류", e)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.")
    }

    private fun build(
        status: HttpStatus,
        code: String,
        message: String?,
    ): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status).body(ApiResponse.fail(code, message ?: status.reasonPhrase))
}
