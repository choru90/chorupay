/*
 * Chorupay - 멀티 모듈 루트 설정
 *
 * [모듈 구성 / 의존 방향]
 *   chorupay-api  ──▶ chorupay-infrastructure ──▶ chorupay-core
 *        └─────────────────────────────────────────▶ chorupay-core
 *
 * - chorupay-core           : 외부 프레임워크 의존을 최소화한 순수 도메인 + 애플리케이션(유스케이스) 계층
 * - chorupay-infrastructure : 아웃바운드 어댑터(JPA, Redis 분산락, Kafka Outbox) 구현
 * - chorupay-api            : 인바운드 어댑터(REST Controller) + 부트 실행 모듈
 */
rootProject.name = "chorupay"

include(
    "chorupay-core",
    "chorupay-infrastructure",
    "chorupay-api",
)
