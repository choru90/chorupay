package com.chorupay.core.domain.wallet

import com.chorupay.core.domain.exception.InvalidAmountException

/**
 * 금액을 표현하는 값 객체(Value Object).
 *
 * 초루머니는 소수점 없는 원(KRW) 단위 정수로 다룬다. 부동소수점 오차를 원천 차단하기 위해
 * 내부 표현은 [Long] 을 사용한다. (예: 1,000원 → Money(1000))
 *
 * - 불변(immutable) 이며 음수가 될 수 없다. (잔액/거래 금액의 도메인 불변식)
 * - 산술 연산 시 새로운 인스턴스를 반환한다.
 *
 * @property amount 원 단위 금액 (0 이상)
 */
@JvmInline
value class Money(val amount: Long) : Comparable<Money> {

    init {
        // 음수 금액은 도메인적으로 허용되지 않는다.
        if (amount < 0) {
            throw InvalidAmountException("금액은 0 이상이어야 합니다. 입력값=$amount")
        }
    }

    /** 두 금액을 더한 새 [Money] 를 반환한다. */
    operator fun plus(other: Money): Money = Money(this.amount + other.amount)

    /**
     * 두 금액을 뺀 새 [Money] 를 반환한다.
     * 결과가 음수가 되면 [InvalidAmountException] 이 발생한다.
     * (잔액 부족 판단은 도메인 메서드에서 선제적으로 수행하고, 여기서는 최종 방어선 역할)
     */
    operator fun minus(other: Money): Money = Money(this.amount - other.amount)

    /** [factor] 배 한 금액을 반환한다. (수수료/배수 계산 등) */
    operator fun times(factor: Long): Money = Money(this.amount * factor)

    /** 0원 여부 */
    fun isZero(): Boolean = amount == 0L

    /** 양수(0 초과) 여부 */
    fun isPositive(): Boolean = amount > 0

    /** [other] 보다 크거나 같은지 */
    fun isGreaterThanOrEqual(other: Money): Boolean = this.amount >= other.amount

    override fun compareTo(other: Money): Int = this.amount.compareTo(other.amount)

    override fun toString(): String = "$amount 원"

    companion object {
        /** 0원 상수 */
        val ZERO: Money = Money(0L)

        /** 팩토리 메서드 - 가독성을 위한 명시적 생성 경로 */
        fun of(amount: Long): Money = Money(amount)

        /** 정수 리터럴 편의 생성 */
        fun of(amount: Int): Money = Money(amount.toLong())
    }
}
