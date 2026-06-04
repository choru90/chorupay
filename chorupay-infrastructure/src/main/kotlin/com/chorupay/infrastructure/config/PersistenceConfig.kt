package com.chorupay.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * 영속성(JPA) 설정.
 *
 * JPA 엔티티/리포지토리 스캔 범위를 infrastructure 패키지로 고정한다. 이로써 영속성 기술 의존을
 * infrastructure 에 캡슐화하고, api 모듈은 spring-data-jpa 를 직접 의존하지 않아도 된다.
 *
 * @EnableTransactionManagement : 분산락 Aspect 의 REQUIRES_NEW 트랜잭션 등 선언적 트랜잭션을 활성화.
 */
@Configuration
@EnableJpaRepositories(basePackages = ["com.chorupay.infrastructure"])
@EntityScan(basePackages = ["com.chorupay.infrastructure"])
@EnableTransactionManagement
class PersistenceConfig
