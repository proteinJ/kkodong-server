package com.kkodong.server.domain.enrollment.domain;

/**
 * 등원 신청 상태. enrollment_applications.status CHECK와 짝을 이룬다.
 *
 * <ul>
 *   <li>{@code PENDING} — 견주가 제출(KG-06/07)하고 점주 승인을 기다리는 상태.
 *       PN-07 접수함과 홈 배지가 이 수를 센다</li>
 *   <li>{@code APPROVED} — 점주 승인. ★ 이 시점에 원생(enrollment)이 생긴다</li>
 *   <li>{@code REJECTED} — 점주 거절. 사유가 견주에게 전달된다</li>
 *   <li>{@code CANCELLED} — 견주가 스스로 철회</li>
 * </ul>
 */
public enum ApplicationStatus {
    PENDING, APPROVED, REJECTED, CANCELLED
}
