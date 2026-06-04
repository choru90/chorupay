package com.chorupay.infrastructure.lock

import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

/**
 * SpEL(Spring Expression Language) 파서 유틸.
 *
 * `@DistributedLock(key = "'wallet:charge:' + #command.userId")` 처럼 선언된 키 표현식을
 * 실제 메서드 인자 값으로 평가하여 동적인 락 키 문자열을 만든다.
 *
 * 예) 파라미터명이 ["command"], 인자값이 ChargeWalletCommand(userId=ABC) 이고
 *     key = "'wallet:charge:' + #command.userId" 이면 → "wallet:charge:ABC"
 */
object CustomSpringElParser {

    private val parser = SpelExpressionParser()

    /**
     * @param parameterNames 대상 메서드의 파라미터 이름 배열
     * @param args 실제 호출 인자 배열 (parameterNames 와 동일 순서)
     * @param key SpEL 키 표현식
     * @return 평가된 락 키 문자열
     */
    fun getDynamicValue(parameterNames: Array<String>, args: Array<Any?>, key: String): String {
        val context = StandardEvaluationContext()
        for (i in parameterNames.indices) {
            context.setVariable(parameterNames[i], args[i])
        }
        return parser.parseExpression(key).getValue(context, String::class.java)
            ?: error("분산락 키 표현식 평가 결과가 null 입니다. key=$key")
    }
}
