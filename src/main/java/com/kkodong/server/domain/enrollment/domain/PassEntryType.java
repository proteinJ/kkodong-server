package com.kkodong.server.domain.enrollment.domain;

/**
 * 이용권 변동 유형. pass_ledger.entry_type CHECK와 짝을 이룬다.
 *
 * <p>원장(ledger)은 append-only다 — 잘못 넣었으면 UPDATE하지 않고 반대 방향 행을 넣는다.
 * 이것이 FR-PN12-01(감사 로그)과 FR-PN09-04(되돌리기)의 근거다.
 */
public enum PassEntryType {
    /** 발급. delta = +총 회차 */
    GRANT,
    /** 등원 차감. delta = -1 */
    DEDUCT,
    /** 되돌리기·취소로 인한 복원. delta = +1 */
    RESTORE,
    /** 기간 연장. 회차와 무관하므로 delta = 0 */
    EXTEND,
    REFUND,
    /** 수동 조정 */
    ADJUST
}
