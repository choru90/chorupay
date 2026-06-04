package com.chorupay.core.domain

import com.chorupay.core.domain.exception.InsufficientBalanceException
import com.chorupay.core.domain.wallet.Money
import com.chorupay.core.domain.wallet.Wallet
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wallet 도메인 규칙 검증.
 * - 충전/차감의 잔액 반영
 * - 잔액 부족 시 Fast-Fail
 * - 복식부기 원장 합계와 잔액의 정합성
 */
class WalletTest {

    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun newWallet(): Wallet = Wallet.create(userId = UUID.randomUUID(), now = now)

    @Test
    fun `충전하면 잔액이 증가하고 CREDIT 원장이 생성된다`() {
        val wallet = newWallet()
        val result = wallet.charge(Money.of(10_000), UUID.randomUUID(), now)

        assertEquals(10_000, result.wallet.balance.amount)
        assertEquals(10_000, result.ledgerEntry.signedAmount())
    }

    @Test
    fun `잔액이 충분하면 차감되고 DEBIT 원장이 생성된다`() {
        val wallet = newWallet()
        wallet.charge(Money.of(10_000), UUID.randomUUID(), now)

        val result = wallet.withdraw(Money.of(3_000), UUID.randomUUID(), now)

        assertEquals(7_000, result.wallet.balance.amount)
        assertEquals(-3_000, result.ledgerEntry.signedAmount())
    }

    @Test
    fun `잔액이 부족하면 차감은 InsufficientBalanceException 으로 즉시 실패한다`() {
        val wallet = newWallet()
        wallet.charge(Money.of(1_000), UUID.randomUUID(), now)

        assertFalse(wallet.canWithdraw(Money.of(2_000)))
        assertFailsWith<InsufficientBalanceException> {
            wallet.withdraw(Money.of(2_000), UUID.randomUUID(), now)
        }
        // 실패 시 잔액은 변하지 않아야 한다.
        assertEquals(1_000, wallet.balance.amount)
    }

    @Test
    fun `원장 합계와 잔액의 정합성이 유지된다`() {
        val wallet = newWallet()
        val e1 = wallet.charge(Money.of(10_000), UUID.randomUUID(), now).ledgerEntry
        val e2 = wallet.withdraw(Money.of(4_000), UUID.randomUUID(), now).ledgerEntry
        val e3 = wallet.charge(Money.of(2_500), UUID.randomUUID(), now).ledgerEntry

        // 10000 - 4000 + 2500 = 8500
        assertEquals(8_500, wallet.balance.amount)
        assertTrue(wallet.isConsistentWith(listOf(e1, e2, e3)))
    }
}
