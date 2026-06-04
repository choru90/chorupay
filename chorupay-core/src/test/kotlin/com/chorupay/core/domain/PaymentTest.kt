package com.chorupay.core.domain

import com.chorupay.core.domain.exception.IllegalPaymentStateTransitionException
import com.chorupay.core.domain.payment.Payment
import com.chorupay.core.domain.payment.PaymentStatus
import com.chorupay.core.domain.wallet.Money
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Payment 상태 머신 검증.
 * 허용 전이(READY→SUCCESS→CANCELED, READY→FAIL)와 금지 전이가 규칙대로 동작하는지 확인한다.
 */
class PaymentTest {

    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun newPayment(): Payment = Payment.create(
        walletId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        orderId = "ORDER-1",
        amount = Money.of(5_000),
        idempotencyKey = UUID.randomUUID().toString(),
        now = now,
    )

    @Test
    fun `READY 에서 SUCCESS 로 전이할 수 있다`() {
        val payment = newPayment()
        payment.markSuccess(now)
        assertEquals(PaymentStatus.SUCCESS, payment.status)
    }

    @Test
    fun `SUCCESS 한 결제는 CANCELED 로 취소할 수 있다`() {
        val payment = newPayment()
        payment.markSuccess(now)
        payment.cancel(now)
        assertEquals(PaymentStatus.CANCELED, payment.status)
        assertTrue(payment.isFinalized())
    }

    @Test
    fun `READY 에서 FAIL 로 전이하면 사유가 기록된다`() {
        val payment = newPayment()
        payment.markFail("INSUFFICIENT_BALANCE", now)
        assertEquals(PaymentStatus.FAIL, payment.status)
        assertEquals("INSUFFICIENT_BALANCE", payment.failureReason)
    }

    @Test
    fun `FAIL 한 결제는 더 이상 전이할 수 없다`() {
        val payment = newPayment()
        payment.markFail("INSUFFICIENT_BALANCE", now)
        assertFailsWith<IllegalPaymentStateTransitionException> {
            payment.markSuccess(now)
        }
    }

    @Test
    fun `READY 가 아닌 상태에서 취소를 시도하면 실패한다`() {
        val payment = newPayment()
        // READY 상태에서 곧바로 cancel 은 허용되지 않는다 (SUCCESS 이후에만 가능)
        assertFailsWith<IllegalPaymentStateTransitionException> {
            payment.cancel(now)
        }
    }
}
