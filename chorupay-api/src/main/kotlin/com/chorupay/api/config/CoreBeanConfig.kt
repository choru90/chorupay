package com.chorupay.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * core 애플리케이션 서비스가 필요로 하는 공용 빈 구성.
 */
@Configuration
class CoreBeanConfig {

    /**
     * 도메인/서비스 전반에서 사용하는 시각 공급자.
     * 테스트에서 고정 Clock 으로 대체할 수 있어 시간 의존 로직의 검증이 쉬워진다.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
