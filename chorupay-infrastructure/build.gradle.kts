/*
 * chorupay-infrastructure
 *
 * [성격] 헥사고날 아키텍처의 "아웃바운드 어댑터" 모듈.
 *   - core 가 정의한 아웃바운드 포트(Repository, EventPublisher)의 실제 구현.
 *   - 기술 스택: JPA(PostgreSQL), Redis(Redisson 분산락), Kafka(Transactional Outbox).
 *
 * core 에만 의존한다. (api 를 알지 못한다)
 */
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")   // JPA 엔티티(allopen) - @Entity/@Embeddable 클래스를 open 으로 컴파일
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
    }
}

// kotlin-jpa(allopen) 가 자동으로 처리하지만, no-arg 생성자가 필요한 어노테이션을 명시해 둔다.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    // --- 내부 모듈 ---
    implementation(project(":chorupay-core"))

    // --- Kotlin ---
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // --- 영속성 (JPA + PostgreSQL) ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // --- Redis / 분산락 (Redisson) ---
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.redisson:redisson-spring-boot-starter:3.30.0")

    // --- 메시징 (Kafka) ---
    implementation("org.springframework.kafka:spring-kafka")

    // --- AOP (분산락 Aspect) ---
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // --- 스케줄링/검증 등 공통 ---
    implementation("org.springframework.boot:spring-boot-starter")

    // --- 테스트 ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
