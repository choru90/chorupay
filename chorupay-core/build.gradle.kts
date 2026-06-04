/*
 * chorupay-core
 *
 * [성격] 헥사고날 아키텍처의 "안쪽(도메인 + 애플리케이션)" 모듈.
 *   - 도메인 모델(Wallet, LedgerEntry, Payment ...)은 프레임워크 의존이 전혀 없는 순수 Kotlin.
 *   - 애플리케이션 서비스(유스케이스 구현)는 트랜잭션 경계/컴포넌트 등록을 위해
 *     spring-context, spring-tx 만 최소한으로 사용한다. (JPA/Redis/Kafka 등 인프라 기술은 금지)
 *
 * [중요] @DistributedLock "어노테이션"은 이 모듈(common.lock)에 정의한다.
 *        실제 AOP 구현체는 infrastructure 에 있으나, 어노테이션 자체를 core 에 두어
 *        infrastructure → core 단방향 의존을 유지한다. (순환참조 방지)
 *
 * 이 모듈은 라이브러리 모듈이므로 spring-boot 플러그인(bootJar)을 적용하지 않는다.
 */
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        // 버전 정렬을 위해 Spring Boot BOM 만 가져온다. (부트 플러그인은 적용하지 않음)
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
    }
}

dependencies {
    // --- Kotlin ---
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // --- 애플리케이션 계층 최소 의존 ---
    // @Service 컴포넌트 등록 및 @Transactional 트랜잭션 경계 선언만을 위해 사용한다.
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")

    // --- 테스트 ---
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        // JSR-305(@Nullable 등) 어노테이션을 엄격하게 해석 → 플랫폼 타입 NPE 방지
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
