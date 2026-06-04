package com.chorupay.core.domain.payment

/**
 * 결제 상태 머신.
 *
 * 상태 전이 다이어그램:
 *
 *            ┌──────────┐  success   ┌───────────┐  cancel   ┌────────────┐
 *            │  READY   │ ─────────▶ │  SUCCESS  │ ────────▶ │  CANCELED  │
 *            └──────────┘            └───────────┘           └────────────┘
 *                 │
 *                 │ fail
 *                 ▼
 *            ┌──────────┐
 *            │   FAIL   │   (종료 상태)
 *            └──────────┘
 *
 * - READY    : 결제 요청 접수. 아직 차감/승인 전.
 * - SUCCESS  : 잔액 차감 및 승인 완료.
 * - FAIL     : 잔액 부족/검증 실패 등으로 결제 실패. (종료)
 * - CANCELED : 성공한 결제를 취소(환불). (종료)
 *
 * 각 상태는 자신에게서 전이 가능한 다음 상태 집합([allowedTransitions])을 알고 있다.
 * 이 집합에 없는 전이는 도메인 규칙 위반이다.
 */
enum class PaymentStatus {
    READY {
        override val allowedTransitions: Set<PaymentStatus>
            get() = setOf(SUCCESS, FAIL)
    },
    SUCCESS {
        override val allowedTransitions: Set<PaymentStatus>
            get() = setOf(CANCELED)
    },
    FAIL {
        override val allowedTransitions: Set<PaymentStatus>
            get() = emptySet()
    },
    CANCELED {
        override val allowedTransitions: Set<PaymentStatus>
            get() = emptySet()
    },
    ;

    /** 이 상태에서 전이 가능한 다음 상태 집합. */
    abstract val allowedTransitions: Set<PaymentStatus>

    /** [target] 으로의 전이가 허용되는지 여부. */
    fun canTransitionTo(target: PaymentStatus): Boolean = target in allowedTransitions

    /** 더 이상 전이가 불가능한 종료 상태인지 여부. */
    fun isTerminal(): Boolean = allowedTransitions.isEmpty()
}
