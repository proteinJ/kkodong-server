package com.kkodong.server.domain.reservation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 등원/하원(PN-08 대시보드, PN-09 출석 체크). 유치원 전용이다 —
 * 미용실·병원에는 등원 개념이 없고 예약이 곧 이용이다.
 *
 * <p><b>예약 승인 시 SCHEDULED 행이 만들어진다.</b> 실제 등원 처리와 이용권 차감은
 * PN-09에서 일어나며 아직 구현하지 않았다(예약을 먼저 세우기로 해서). 이 클래스는
 * 지금 그 전환의 착지점 역할만 한다.
 *
 * <p>⚠️ 등원 확정과 이용권 차감은 반드시 하나의 트랜잭션이어야 한다(FR-PN09-03).
 * 하나라도 밖에 있으면 "등원은 됐는데 회차가 안 깎인" 행이 남고, 그건 조회로 찾아낼
 * 방법이 없다. PN-09 구현 시 이 주석을 다시 볼 것.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "attendances")
public class Attendance {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    /**
     * 어느 예약에서 전환됐는지. null이면 예약 없이 그날 바로 온 경우(워크인)다.
     * 워크인을 막지 않는 이유: 막으면 점주가 출석을 못 찍고, 그러면 이용권도 차감되지
     * 않아 매출이 새는 쪽이 훨씬 나쁘다.
     */
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.SCHEDULED; // 컨버터 자동 적용

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "checked_out_at")
    private OffsetDateTime checkedOutAt;

    @Column(name = "checked_in_by_user_id")
    private UUID checkedInByUserId;

    @Column(name = "checked_out_by_user_id")
    private UUID checkedOutByUserId;

    /** 이 등원으로 차감된 이용권. 기간권이면 passLedgerId는 null이다(차감할 회차가 없다). */
    @Column(name = "pass_id")
    private UUID passId;

    @Column(name = "pass_ledger_id")
    private UUID passLedgerId;

    /** 되돌리기(FR-PN09-04). 행을 지우지 않는다 — 되돌린 이력 자체가 분쟁 대응의 근거다. */
    @Column(name = "reverted_at")
    private OffsetDateTime revertedAt;

    @Column(name = "reverted_by_user_id")
    private UUID revertedByUserId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 예약 취소에 따라 등원 예정을 함께 접는다.
     *
     * <p>이미 등원한 뒤라면 건드리지 않는다 — 온 사실과 차감된 회차를 되돌리는 것은
     * 취소가 아니라 되돌리기(PN-09)의 몫이고, 거기엔 이용권 복원이 따라붙어야 한다.
     *
     * @return 실제로 취소했으면 true
     */
    public boolean cancelIfScheduled() {
        if (status != AttendanceStatus.SCHEDULED) return false;
        this.status = AttendanceStatus.CANCELLED;
        return true;
    }
}
