package com.chorupay.core.application.service

import com.chorupay.core.application.port.`in`.ChargeWalletCommand
import com.chorupay.core.application.port.`in`.ChargeWalletResult
import com.chorupay.core.application.port.`in`.ChargeWalletUseCase
import com.chorupay.core.application.port.out.EventPublisher
import com.chorupay.core.application.port.out.LedgerRepository
import com.chorupay.core.application.port.out.WalletRepository
import com.chorupay.core.common.lock.DistributedLock
import com.chorupay.core.domain.event.WalletChargedEvent
import com.chorupay.core.domain.wallet.Money
import com.chorupay.core.domain.wallet.Wallet
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/**
 * 지갑 충전 애플리케이션 서비스.
 *
 * [동시성 정합성의 핵심]
 *  - [DistributedLock] 으로 "사용자 단위(wallet:{userId})" 쓰기를 직렬화한다. 대규모 트래픽에서
 *    동일 지갑에 대한 동시 충전이 들어와도 잔액 갱신이 원자적으로 순차 처리된다.
 *  - 락 키를 userId 로 잡는 이유: 신규 사용자는 아직 walletId 가 없을 수 있으므로,
 *    "사용자" 를 락 단위로 삼아 지갑 생성 경합까지 함께 보호한다.
 *  - 실제 트랜잭션은 분산락 Aspect 가 REQUIRES_NEW 로 감싸 실행하며, 커밋 후 락을 해제한다.
 *
 * [멱등성]
 *  idempotencyKey 로부터 결정적(deterministic) transactionId 를 만들고, 이미 처리된
 *  트랜잭션이면 현재 잔액으로 멱등 응답한다. (네트워크 재시도/중복 클릭 안전)
 *
 * @property walletRepository 지갑 영속성 포트
 * @property ledgerRepository 원장 영속성 포트
 * @property eventPublisher 도메인 이벤트 발행 포트 (Outbox)
 * @property clock 시각 공급자 (테스트 용이성)
 */
@Service
class WalletService(
    private val walletRepository: WalletRepository,
    private val ledgerRepository: LedgerRepository,
    private val eventPublisher: EventPublisher,
    private val clock: Clock,
) : ChargeWalletUseCase {

    @DistributedLock(
        key = "'wallet:charge:' + #command.userId",
        waitTime = 3L,
        leaseTime = 5L,
    )
    override fun charge(command: ChargeWalletCommand): ChargeWalletResult {
        val now = clock.instant()
        val amount = Money.of(command.amount)

        // 멱등키 → 결정적 트랜잭션 ID. 같은 요청은 항상 같은 ID 를 만든다.
        val transactionId = deterministicTransactionId(command.idempotencyKey)

        // 1) 지갑 조회 또는 신규 생성 (사용자별 1지갑 보장 - 락으로 경합 차단됨)
        val wallet: Wallet = walletRepository.findByUserId(command.userId)
            ?: walletRepository.save(Wallet.create(userId = command.userId, now = now))

        // 2) 멱등성 검사: 이미 처리된 트랜잭션이면 중복 충전하지 않고 현재 잔액으로 응답.
        if (ledgerRepository.existsByTransactionId(transactionId)) {
            return ChargeWalletResult(
                walletId = wallet.id,
                userId = wallet.userId,
                chargedAmount = command.amount,
                balance = wallet.balance.amount,
                transactionId = transactionId,
            )
        }

        // 3) 도메인 연산: 충전 → 갱신된 지갑 + CREDIT 원장 기입
        val mutation = wallet.charge(
            amount = amount,
            transactionId = transactionId,
            now = now,
        )

        // 4) 원자적 영속화 (지갑 + 원장). 동일 트랜잭션 내에서 함께 커밋된다.
        val savedWallet = walletRepository.save(mutation.wallet)
        ledgerRepository.append(mutation.ledgerEntry)

        // 5) 도메인 이벤트 발행 (Outbox 적재 → 동일 트랜잭션 커밋)
        eventPublisher.publish(
            WalletChargedEvent(
                eventId = UUID.randomUUID(),
                aggregateId = savedWallet.id.toString(),
                walletId = savedWallet.id,
                userId = savedWallet.userId,
                transactionId = transactionId,
                chargedAmount = amount.amount,
                balanceAfter = savedWallet.balance.amount,
                occurredAt = now,
            ),
        )

        return ChargeWalletResult(
            walletId = savedWallet.id,
            userId = savedWallet.userId,
            chargedAmount = amount.amount,
            balance = savedWallet.balance.amount,
            transactionId = transactionId,
        )
    }

    /**
     * 멱등키로부터 결정적 UUID 를 생성한다.
     * 같은 문자열은 항상 같은 UUID 를 만들어 충전 트랜잭션의 멱등성을 보장한다.
     */
    private fun deterministicTransactionId(idempotencyKey: String): UUID =
        UUID.nameUUIDFromBytes("charge:$idempotencyKey".toByteArray(Charsets.UTF_8))
}
