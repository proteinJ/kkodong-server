package com.kkodong.server.domain.reservation.domain;

/**
 * 등원 상태. attendances.status CHECK와 짝을 이룬다.
 *
 * <p>⚠️ 하원은 별도 상태가 아니다 — {@code checkedOutAt}이 채워진 것으로 본다.
 * 상태로 만들면 "하원했지만 등원 안 함" 같은 불가능한 조합이 표현 가능해진다.
 */
public enum AttendanceStatus {
    /** 예약 승인으로 만들어진 등원 예정. */
    SCHEDULED,
    ATTENDED,
    ABSENT,
    CANCELLED
}
