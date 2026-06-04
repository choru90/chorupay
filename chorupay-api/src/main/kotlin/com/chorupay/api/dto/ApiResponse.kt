package com.chorupay.api.dto

import java.time.Instant

/**
 * 표준 API 응답 래퍼.
 *
 * 성공/실패를 일관된 구조로 감싸 클라이언트 파싱을 단순화한다.
 *
 * @property success 처리 성공 여부
 * @property data 성공 시 페이로드 (실패 시 null)
 * @property error 실패 시 에러 정보 (성공 시 null)
 * @property timestamp 응답 생성 시각
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorBody?,
    val timestamp: Instant,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> =
            ApiResponse(success = true, data = data, error = null, timestamp = Instant.now())

        fun fail(code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(
                success = false,
                data = null,
                error = ErrorBody(code, message),
                timestamp = Instant.now(),
            )
    }
}

/**
 * 에러 본문.
 * @property code 안정적인 에러 코드 (클라이언트 분기/로깅용)
 * @property message 사람이 읽을 수 있는 설명
 */
data class ErrorBody(
    val code: String,
    val message: String,
)
