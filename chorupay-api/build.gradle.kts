/*
 * chorupay-api
 *
 * [성격] 헥사고날 아키텍처의 "인바운드 어댑터" + 부트스트랩(실행) 모듈.
 *   - REST Controller, 요청/응답 DTO, 전역 예외 핸들러, Rate Limiting 필터.
 *   - core(유스케이스 포트) 와 infrastructure(어댑터 구현) 를 조립하여 실행 가능한 애플리케이션을 만든다.
 *
 * 유일하게 spring-boot 플러그인을 적용하여 실행 가능한 fat jar(bootJar) 를 생성한다.
 */
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // --- 내부 모듈 조립 ---
    implementation(project(":chorupay-core"))
    implementation(project(":chorupay-infrastructure"))

    // --- Web / 검증 ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Kotlin / JSON ---
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // --- 모니터링 ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- 테스트 ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
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

// 실행 가능한 jar 메인 클래스 지정
springBoot {
    mainClass.set("com.chorupay.api.ChorupayApplicationKt")
}
