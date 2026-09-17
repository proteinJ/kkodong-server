package com.kkodong.server.domain.merchant.domain;

/**
 * 스태프 소속 상태. merchant_staff.status CHECK와 짝을 이룬다.
 *
 * <ul>
 *   <li>{@code PENDING} — 선생님이 소속 신청 후 원장 승인 대기(PN-03)</li>
 *   <li>{@code ACTIVE} — 정상 소속</li>
 *   <li>{@code RESIGNED} — 퇴사. ⚠️ FR-PN18-02 — 행을 지우지 않는다.
 *       이 사람이 쓴 알림장·출석 처리 이력이 작성자로 참조하고 있고 그 이력은 보존해야 한다.
 *       접근 권한 회수는 이 상태로만 한다</li>
 * </ul>
 */
public enum StaffStatus {
    PENDING, ACTIVE, RESIGNED
}
