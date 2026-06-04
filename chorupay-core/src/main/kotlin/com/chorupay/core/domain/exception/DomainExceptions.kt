package com.chorupay.core.domain.exception

/**
 * 도메인 계층의 최상위 예외.
 *
 * 모든 도메인 규칙 위반은 이 타입을 상속한다. API 계층의 전역 예외 핸들러는
 * 이 타입 계층을 기준으로 적절한 HTTP 상태/에러 코드로 변환한다.
 *
 * @property errorCode 클라이언트/로그에서 식별 가능한 안정적인 에러 코드
 */
sealed class DomainException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** 금액이 도메인 규칙(0 이상 등)을 위반한 경우. */
class InvalidAmountException(
    message: String,
) : DomainException(errorCode = "INVALID_AMOUNT", message = message)

/** 지갑 잔액이 부족하여 차감/결제를 진행할 수 없는 경우. */
class InsufficientBalanceException(
    val walletId: String,
    val balance: Long,
    val requested: Long,
) : DomainException(
    errorCode = "INSUFFICIENT_BALANCE",
    message = "잔액이 부족합니다. walletId=$walletId, balance=$balance, requested=$requested",
)

/** 결제 상태 머신에서 허용되지 않는 전이를 시도한 경우. */
class IllegalPaymentStateTransitionException(
    val from: String,
    val to: String,
) : DomainException(
    errorCode = "ILLEGAL_PAYMENT_STATE_TRANSITION",
    message = "허용되지 않은 결제 상태 전이입니다. $from -> $to",
)

/**
 * 복식부기 원장 합계와 지갑 잔액이 일치하지 않는 정합성 위반.
 * 대규모 동시성 환경에서 데이터 정합성 검증의 핵심 신호다.
 */
class BalanceInconsistencyException(
    val walletId: String,
    val walletBalance: Long,
    val ledgerBalance: Long,
) : DomainException(
    errorCode = "BALANCE_INCONSISTENCY",
    message = "지갑 잔액과 원장 합계가 불일치합니다. " +
        "walletId=$walletId, walletBalance=$walletBalance, ledgerBalance=$ledgerBalance",
)

/** 조회 대상 엔티티(지갑/결제 등)를 찾을 수 없는 경우. */
class EntityNotFoundException(
    val entityType: String,
    val identifier: String,
) : DomainException(
    errorCode = "ENTITY_NOT_FOUND",
    message = "$entityType 를 찾을 수 없습니다. identifier=$identifier",
)
