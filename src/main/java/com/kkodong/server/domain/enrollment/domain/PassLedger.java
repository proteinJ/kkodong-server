package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이용권 변동 이력(PN-12, FR-PN12-01). 누가·언제·무엇을 바꿨는지 전부 남긴다.
 *
 * <p>되돌리기(FR-PN09-04)와 분쟁 대응의 유일한 근거이며, {@link Pass#getRemainingCount()}의
 * 진실 원본이다. 잔여가 이상하면 이 원장을 처음부터 더해서 어느 지점에서 어긋났는지 찾는다.
 *
 * <p>⚠️ <b>append-only</b>다. UPDATE·DELETE 하지 않는다 — 잘못 넣었으면 반대 방향 행을
 * 추가한다. 그래서 이 클래스에는 상태를 바꾸는 메서드가 하나도 없다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "pass_ledger")
public class PassLedger {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "pass_id", nullable = false)
    private UUID passId;

    @Column(name = "entry_type", nullable = false)
    private PassEntryType entryType; // PassEntryTypeConverter 자동 적용

    /** 회차 변동량. 차감이면 음수, 복원이면 양수. 기간 연장 등 회차와 무관하면 0. */
    @Column(nullable = false)
    private Integer delta;

    /**
     * 이 행을 적용한 직후의 잔여. 기간권이면 null.
     * 스냅샷을 남겨야 중간에 계산이 어긋났을 때 어느 지점에서 깨졌는지 찾을 수 있다.
     */
    @Column(name = "balance_after")
    private Integer balanceAfter;

    private String reason;

    /** 처리자. 시스템 자동 처리(만료 등)면 null. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /** 어느 등원에서 비롯된 차감인지. KG-13 "등원일별 차감 내역"이 이걸로 만들어진다. */
    @Column(name = "source_attendance_id")
    private UUID sourceAttendanceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 발급 한 줄. 이용권의 첫 행이며, 원장을 처음부터 더하면 현재 잔여가 나와야 한다. */
    public static PassLedger grant(UUID passId, Integer totalCount, UUID actorUserId, String reason) {
        return PassLedger.builder()
                .passId(passId)
                .entryType(PassEntryType.GRANT)
                .delta(totalCount == null ? 0 : totalCount) // 기간권은 회차가 없다
                .balanceAfter(totalCount)
                .actorUserId(actorUserId)
                .reason(reason == null ? "이용권 발급" : reason)
                .build();
    }

    /** 기간 연장 한 줄. 회차와 무관하므로 delta는 0이다. */
    public static PassLedger extension(UUID passId, Integer balanceAfter,
                                       UUID actorUserId, String reason) {
        return PassLedger.builder()
                .passId(passId)
                .entryType(PassEntryType.EXTEND)
                .delta(0)
                .balanceAfter(balanceAfter)
                .actorUserId(actorUserId)
                .reason(reason == null ? "기간 연장" : reason)
                .build();
    }

    /**
     * 환불 한 줄. 남아 있던 회차를 전부 걷어낸 것으로 기록한다 —
     * 원장을 처음부터 더했을 때 잔여 0이 나와야 장부가 맞는다.
     */
    public static PassLedger refund(UUID passId, Integer removedCount,
                                    UUID actorUserId, String reason) {
        return PassLedger.builder()
                .passId(passId)
                .entryType(PassEntryType.REFUND)
                .delta(removedCount == null ? 0 : -removedCount)
                .balanceAfter(removedCount == null ? null : 0)
                .actorUserId(actorUserId)
                .reason(reason == null ? "환불" : reason)
                .build();
    }

    /** 수동 조정 한 줄. ⚠️ 사유가 필수다 — 근거 없는 회차 변동은 분쟁 때 방어할 수 없다. */
    public static PassLedger adjustment(UUID passId, int delta, Integer balanceAfter,
                                        UUID actorUserId, String reason) {
        return PassLedger.builder()
                .passId(passId)
                .entryType(PassEntryType.ADJUST)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .actorUserId(actorUserId)
                .reason(reason)
                .build();
    }

    /** 등원 차감 한 줄. delta는 항상 -1이다. */
    public static PassLedger deduction(UUID passId, Integer balanceAfter,
                                       UUID actorUserId, UUID attendanceId) {
        return PassLedger.builder()
                .passId(passId)
                .entryType(PassEntryType.DEDUCT)
                .delta(-1)
                .balanceAfter(balanceAfter)
                .actorUserId(actorUserId)
                .sourceAttendanceId(attendanceId)
                .reason("등원 확정")
                .build();
    }

    /** 되돌리기 복원 한 줄. 차감 행을 지우지 않고 반대 방향 행을 넣는다. */
    public static PassLedger restoration(UUID passId, Integer balanceAfter,
                                         UUID actorUserId, UUID attendanceId, String reason) {
        return PassLedger.builder()
                .passId(passId)
                .entryType(PassEntryType.RESTORE)
                .delta(1)
                .balanceAfter(balanceAfter)
                .actorUserId(actorUserId)
                .sourceAttendanceId(attendanceId)
                .reason(reason == null ? "등원 되돌리기" : reason)
                .build();
    }
}
