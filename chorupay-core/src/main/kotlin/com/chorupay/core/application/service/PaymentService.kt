package com.chorupay.core.application.service

import com.chorupay.core.application.port.`in`.ProcessPaymentCommand
import com.chorupay.core.application.port.`in`.ProcessPaymentResult
import com.chorupay.core.application.port.`in`.ProcessPaymentUseCase
import com.chorupay.core.application.port.out.EventPublisher
import com.chorupay.core.application.port.out.LedgerRepository
import com.chorupay.core.application.port.out.PaymentRepository
import com.chorupay.core.application.port.out.WalletRepository
import com.chorupay.core.common.lock.DistributedLock
import com.chorupay.core.domain.event.PaymentProcessedEvent
import com.chorupay.core.domain.exception.EntityNotFoundException
import com.chorupay.core.domain.exception.InsufficientBalanceException
import com.chorupay.core.domain.payment.Payment
import com.chorupay.core.domain.wallet.Money
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/**
 * 결제 처리 애플리케이션 서비스.
 *
 * [처리 흐름]
 *  1) 멱등성 검사 (동일 idempotencyKey → 기존 결제 결과 반환)
 *  2) 지갑 조회
 *  3) 결제(READY) 생성
 *  4) 잔액 차감 시도
 *     - 성공: 지갑/원장 갱신, 결제 SUCCESS
 *     - 잔액부족: 결제 FAIL (예외를 잡아 도메인 상태로 흡수 → 일관된 결과 반환)
 *  5) 도메인 이벤트 발행 (Outbox)
 *
 * [동시성 정합성]
 *  지갑 단위 분산락으로 "동시 결제로 인한 잔액 초과 차감(oversell)" 을 원천 차단한다.
 *  락 키를 userId 로 잡아 충전 서비스와 동일한 락 도메인을 공유 → 충전/결제 간 경합도 직렬화.
 */
@Service
class PaymentService(
    private val walletRepository: WalletRepository,
    private val paymentRepository: PaymentRepository,
    private val ledgerRepository: LedgerRepository,
    private val eventPublisher: EventPublisher,
    private val clock: Clock,
) : ProcessPaymentUseCase {

    @DistributedLock(
        key = "'wallet:charge:' + #command.userId",
        waitTime = 3L,
        leaseTime = 5L,
    )
    override fun process(command: ProcessPaymentCommand): ProcessPaymentResult {
        val now = clock.instant()

        // 1) 멱등성: 이미 처리된 결제면 그 결과를 그대로 반환한다.
        paymentRepository.findByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            return existing.toResult()
        }

        // 2) 지갑 조회. 결제는 사전 충전된 지갑을 전제로 한다.
        val wallet = walletRepository.findByUserId(command.userId)
            ?: throw EntityNotFoundException("Wallet", "userId=${command.userId}")

        val amount = Money.of(command.amount)

        // 3) 결제 생성 (READY)
        val payment = Payment.create(
            walletId = wallet.id,
            userId = command.userId,
            orderId = command.orderId,
            amount = amount,
            idempotencyKey = command.idempotencyKey,
            now = now,
        )

        // 4) 잔액 차감 시도
        return try {
            val mutation = wallet.withdraw(
                amount = amount,
                transactionId = payment.id,
                now = now,
                description = "결제(orderId=${command.orderId})",
            )

            // 차감 성공 → 영속화 + 결제 SUCCESS
            val savedWallet = walletRepository.save(mutation.wallet)
            ledgerRepository.append(mutation.ledgerEntry)
            payment.markSuccess(now)
            paymentRepository.save(payment)

            publishProcessed(payment, savedWallet.balance.amount, now)
            payment.toResult(balanceAfter = savedWallet.balance.amount)
        } catch (e: InsufficientBalanceException) {
            // 잔액 부족 → 결제 FAIL 로 흡수. 호출자는 일관된 결과 객체를 받는다.
            payment.markFail(reason = e.errorCode, now = now)
            paymentRepository.save(payment)
            publishProcessed(payment, null, now)
            payment.toResult(balanceAfter = null)
        }
    }

    /** 결제 처리 이벤트를 Outbox 로 발행한다. */
    private fun publishProcessed(payment: Payment, balanceAfter: Long?, now: java.time.Instant) {
        eventPublisher.publish(
            PaymentProcessedEvent(
                eventId = UUID.randomUUID(),
                aggregateId = payment.id.toString(),
                paymentId = payment.id,
                walletId = payment.walletId,
                userId = payment.userId,
                orderId = payment.orderId,
                amount = payment.amount.amount,
                status = payment.status.name,
                balanceAfter = balanceAfter,
                occurredAt = now,
            ),
        )
    }

    /** 도메인 Payment → 유스케이스 결과 DTO 변환. */
    private fun Payment.toResult(balanceAfter: Long? = null): ProcessPaymentResult =
        ProcessPaymentResult(
            paymentId = id,
            orderId = orderId,
            status = status.name,
            amount = amount.amount,
            balanceAfter = balanceAfter,
            failureReason = failureReason,
        )
}
