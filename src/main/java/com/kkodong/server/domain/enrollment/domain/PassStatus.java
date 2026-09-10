package com.kkodong.server.domain.enrollment.domain;

/**
 * 이용권 상태. passes.status CHECK와 짝을 이룬다.
 *
 * <p>⚠️ {@code EXHAUSTED}·{@code EXPIRED}는 파생 상태다. 배치로 갱신하지 말고 조회 시
 * 함께 판정할 것 — "잔여 0인데 아직 ACTIVE" 같은 시차가 출석 화면에서 곧바로 사고가 된다.
 * 그래서 사용 가능 여부 판정은 {@link Pass#isUsableOn}이 상태·잔여·만료를 모두 본다.
 */
public enum PassStatus {
    ACTIVE, EXHAUSTED, EXPIRED, REFUNDED, SUSPENDED
}
