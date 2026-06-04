/*
 * Chorupay - 루트 빌드 스크립트
 *
 * [역할]
 *  1. 모든 하위 모듈에서 공통으로 사용할 플러그인의 "버전"을 한 곳에서 선언한다. (apply false)
 *  2. allprojects 공통 메타데이터(group/version)와 저장소를 지정한다.
 *
 * 실제 플러그인 "적용(apply)" 과 의존성 선언은 각 모듈의 build.gradle.kts 에서 수행한다.
 * (모듈별 책임이 다르므로 - core 는 부트 jar 가 필요 없고, api 만 실행 가능 jar 가 필요)
 */
plugins {
    // 버전만 선언하고 루트에는 적용하지 않는다 (apply false).
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.3.2" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.chorupay"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
