package com.chorupay.api.controller

import com.chorupay.api.dto.ApiResponse
import com.chorupay.api.dto.PaymentRequest
import com.chorupay.api.dto.PaymentResponse
import com.chorupay.core.application.port.`in`.ProcessPaymentUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 결제 인바운드 어댑터(REST).
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val processPaymentUseCase: ProcessPaymentUseCase,
) {

    /**
     * 결제 요청 API.
     *
     * `POST /api/v1/payments`
     *
     * 잔액 부족은 예외가 아니라 결과(status=FAIL)로 반환되어 200 으로 응답한다.
     * (클라이언트는 status 로 성공/실패를 분기)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun pay(
        @Valid @RequestBody request: PaymentRequest,
    ): ApiResponse<PaymentResponse> {
        val result = processPaymentUseCase.process(request.toCommand())
        return ApiResponse.ok(PaymentResponse.from(result))
    }
}
