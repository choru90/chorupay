package com.chorupay.api.controller

import com.chorupay.api.dto.ApiResponse
import com.chorupay.api.dto.ChargeRequest
import com.chorupay.api.dto.ChargeResponse
import com.chorupay.core.application.port.`in`.ChargeWalletUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 지갑 인바운드 어댑터(REST).
 *
 * 유스케이스 포트([ChargeWalletUseCase]) 에만 의존한다. 구현(WalletService)을 직접 알지 못한다.
 */
@RestController
@RequestMapping("/api/v1/wallets")
class WalletController(
    private val chargeWalletUseCase: ChargeWalletUseCase,
) {

    /**
     * 지갑 충전 API.
     *
     * `POST /api/v1/wallets/{userId}/charge`
     *
     * - 요청 본문은 @Valid 로 즉시 검증된다. (Fast-Fail)
     * - 동시성은 유스케이스 내부의 분산락으로 보장된다.
     */
    @PostMapping("/{userId}/charge")
    @ResponseStatus(HttpStatus.OK)
    fun charge(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: ChargeRequest,
    ): ApiResponse<ChargeResponse> {
        val result = chargeWalletUseCase.charge(request.toCommand(userId))
        return ApiResponse.ok(ChargeResponse.from(result))
    }
}
