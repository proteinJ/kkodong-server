package com.kkodong.server.domain.enrollment.domain;

/**
 * 원생 재원 상태. enrollments.status CHECK와 짝을 이룬다.
 *
 * <ul>
 *   <li>{@code ACTIVE} — 재원 중. 예약할 수 있는 유일한 상태다</li>
 *   <li>{@code PAUSED} — 휴원. 이용권은 살아 있지만 예약은 받지 않는다</li>
 *   <li>{@code WITHDRAWN} — 퇴원</li>
 * </ul>
 */
public enum EnrollmentStatus {
    ACTIVE, PAUSED, WITHDRAWN
}
