package com.chorupay.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Chorupay 부트스트랩 애플리케이션.
 *
 * [모듈 조립]
 *  - 컴포넌트 스캔 기준 패키지를 "com.chorupay" 로 넓혀 core(서비스)/infrastructure(어댑터)/api 를 모두 포함한다.
 *  - JPA 엔티티/리포지토리 스캔은 infrastructure 모듈의 PersistenceConfig 가 담당한다.
 *    (영속성 기술 의존은 infrastructure 에 캡슐화되어 api 가 spring-data-jpa 를 직접 알 필요가 없다)
 *  - @EnableScheduling : Outbox 폴링 발행 스케줄러 활성화.
 */
@SpringBootApplication(scanBasePackages = ["com.chorupay"])
@EnableScheduling
class ChorupayApplication

fun main(args: Array<String>) {
    runApplication<ChorupayApplication>(*args)
}
