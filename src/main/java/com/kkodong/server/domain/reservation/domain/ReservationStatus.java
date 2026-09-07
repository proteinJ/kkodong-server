package com.kkodong.server.domain.reservation.domain;

/**
 * 예약 상태. reservations.status CHECK와 짝을 이룬다.
 *
 * <p>업종이 늘어도 이 상태머신은 그대로 쓴다 — 유치원·미용실·병원의 예약이 공통으로
 * 갖는 흐름이기 때문이다(V6 merchants 주석 참조).
 *
 * <pre>
 *   REQUESTED ──승인──> CONFIRMED ──등원/이용──> COMPLETED
 *       │                   │
 *       │                   ├──안 옴──> NO_SHOW
 *       ├──거절──> REJECTED └──취소──> CANCELLED
 *       └──취소──> CANCELLED
 * </pre>
 *
 * <p>전이 규칙은 {@link Reservation}의 메서드가 강제한다 — 상태를 직접 세팅하지 말 것.
 */
public enum ReservationStatus {
    REQUESTED, CONFIRMED, REJECTED, CANCELLED, COMPLETED, NO_SHOW;

    /** 더 이상 바뀌지 않는 종착 상태. */
    public boolean isTerminal() {
        return this == REJECTED || this == CANCELLED || this == COMPLETED || this == NO_SHOW;
    }

    /**
     * 일일 정원을 차지하는 상태인지.
     *
     * <p>REQUESTED를 세지 않는 이유: 신청만으로 자리를 잡으면 승인하지 않은 신청이
     * 쌓여 정원이 막힌다. 자리는 점주가 승인한 시점에 확정된다.
     */
    public boolean occupiesCapacity() {
        return this == CONFIRMED || this == COMPLETED;
    }
}
