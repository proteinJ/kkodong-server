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
     * 등원 확정(PN-09, FR-PN09-03).
     *
     * <p>⚠️ 이용권 차감과 반드시 같은 트랜잭션 안에 있어야 한다. 하나라도 밖에 있으면
     * "등원은 됐는데 회차가 안 깎인" 행이 남고, 그건 조회로 찾아낼 방법이 없다.
     * 그래서 차감 결과(passId·passLedgerId)를 인자로 받는다 — 차감 없이 등원만
     * 시키는 호출을 만들기 어렵게 하려는 것이다.
     *
     * @param passLedgerId 기간권이면 null(차감할 회차가 없다)
     */
    public void checkIn(UUID actorUserId, UUID passId, UUID passLedgerId) {
        if (status != AttendanceStatus.SCHEDULED) {
            throw new com.kkodong.server.global.error.BusinessException(
                    com.kkodong.server.global.error.ErrorCode.INVALID_ATTENDANCE_STATUS);
        }
        this.status = AttendanceStatus.ATTENDED;
        this.checkedInAt = OffsetDateTime.now();
        this.checkedInByUserId = actorUserId;
        this.passId = passId;
        this.passLedgerId = passLedgerId;
    }

    /**
     * 하원 처리(PN-09).
     *
     * <p>등원하지 않았으면 하원할 수 없다 — DB의 {@code chk_attendances_checkin_status}와
     * 같은 규칙을 서버에서 먼저 본다.
     */
    public void checkOut(UUID actorUserId) {
        if (status != AttendanceStatus.ATTENDED || checkedInAt == null) {
            throw new com.kkodong.server.global.error.BusinessException(
                    com.kkodong.server.global.error.ErrorCode.INVALID_ATTENDANCE_STATUS);
        }
        this.checkedOutAt = OffsetDateTime.now();
        this.checkedOutByUserId = actorUserId;
    }

    /**
     * 등원 되돌리기(FR-PN09-04). 오처리를 취소하고 등원 예정 상태로 되돌린다.
     *
     * <p>⚠️ 이용권 복원과 같은 트랜잭션이어야 한다. 되돌렸는데 회차가 안 돌아오면
     * 견주는 오지도 않은 날의 회차를 잃는다.
     *
     * <p>행을 지우지 않고 {@code revertedAt}을 남기는 이유: 되돌린 이력 자체가
     * 분쟁 대응의 근거다. "원래 등원 처리됐다가 취소된 것"과 "처음부터 없던 것"은 다르다.
     */
    public void revert(UUID actorUserId) {
        if (status != AttendanceStatus.ATTENDED) {
            throw new com.kkodong.server.global.error.BusinessException(
                    com.kkodong.server.global.error.ErrorCode.INVALID_ATTENDANCE_STATUS);
        }
        this.status = AttendanceStatus.SCHEDULED;
        this.checkedInAt = null;
        this.checkedInByUserId = null;
        this.checkedOutAt = null;
        this.checkedOutByUserId = null;
        this.passId = null;
        this.passLedgerId = null;
        this.revertedAt = OffsetDateTime.now();
        this.revertedByUserId = actorUserId;
    }

    /** 결석 처리(PN-08). 예정이었는데 오지 않은 경우다. 이용권은 차감하지 않는다. */
    public void markAbsent() {
        if (status != AttendanceStatus.SCHEDULED) {
            throw new com.kkodong.server.global.error.BusinessException(
                    com.kkodong.server.global.error.ErrorCode.INVALID_ATTENDANCE_STATUS);
        }
        this.status = AttendanceStatus.ABSENT;
    }

    /** 하원까지 마쳤는지. 대시보드 집계에서 등원 중과 하원 완료를 가른다. */
    public boolean isCheckedOut() {
        return checkedOutAt != null;
    }

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
