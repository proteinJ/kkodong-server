package com.kkodong.server.domain.reservation.domain;

import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 예약(PN-19). V8__add_reservation_attendance_domain.sql의 reservations에 매핑.
 *
 * <p><b>상태 전이를 이 클래스가 강제한다.</b> setter를 열지 않고 confirm/reject/cancel/
 * complete/markNoShow 만 둔 이유는, 상태를 밖에서 직접 넣을 수 있으면 "거절된 예약이
 * 완료로 바뀌는" 조합을 아무도 못 막기 때문이다. 예약은 이용권 차감과 매출로 이어져서
 * 잘못된 전이가 조용히 통과하면 회계가 틀어진다.
 *
 * <p><b>service_date를 startsAt과 따로 두는 이유</b>: "오늘 등원 예정"(PN-08)과 정원 집계가
 * 전부 날짜 단위인데 TIMESTAMPTZ는 UTC로 저장된다. startsAt에서 매번 날짜를 뽑으면
 * 자정 근처 예약이 KST 기준 하루 밀린 날짜로 집계된다. 매장 로컬 날짜를 못박아 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** 탈퇴로 null이 될 수 있다 — 매장의 예약 이력은 남아야 한다. */
    @Column(name = "dog_id")
    private UUID dogId;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "dog_name_snapshot", nullable = false)
    private String dogNameSnapshot;

    /**
     * 유치원 예약은 원생만 할 수 있다(흐름 F-11). 미용실·병원은 원생 개념이 없어 null이다.
     * ⚠️ 업종별 조건부 필수는 CHECK로 표현할 수 없어(merchants 조인이 필요) 서버가 검증한다.
     */
    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "is_all_day", nullable = false)
    @Builder.Default
    private Boolean isAllDay = true;

    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.REQUESTED; // 컨버터 자동 적용

    @Column(nullable = false)
    @Builder.Default
    private ReservationSource source = ReservationSource.OWNER;

    private String note; // 견주 요청사항

    @Column(name = "responded_by_user_id")
    private UUID respondedByUserId;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 승인(PN-19). 이 시점에 자리가 확정되고 등원 예정(attendances)이 만들어진다.
     *
     * <p>⚠️ 정원 검사는 여기서 하지 않는다 — 다른 예약들을 세어야 해서 엔티티가 알 수 없다.
     * 호출 전에 반드시 정원을 확인할 것(ReservationService.confirm 참조).
     */
    public void confirm(UUID actorUserId) {
        ensureConfirmable();
        this.status = ReservationStatus.CONFIRMED;
        this.respondedByUserId = actorUserId;
        this.respondedAt = OffsetDateTime.now();
    }

    /** 거절(PN-19). 사유는 견주에게 그대로 전달된다(FR-PN07-01과 같은 규칙). */
    public void reject(UUID actorUserId, String reason) {
        requireStatus(ReservationStatus.REQUESTED);
        this.status = ReservationStatus.REJECTED;
        this.respondedByUserId = actorUserId;
        this.respondedAt = OffsetDateTime.now();
        this.rejectReason = reason;
    }

    /**
     * 취소. 신청 중이든 승인 후든 취소할 수 있다 — 당일 사정으로 못 가는 일이 정상이다.
     * 이미 끝난 예약(완료·노쇼·거절·기취소)은 취소 대상이 아니다.
     */
    public void cancel(UUID actorUserId, String reason) {
        if (status.isTerminal()) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATUS);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledByUserId = actorUserId;
        this.cancelledAt = OffsetDateTime.now();
        this.cancelReason = reason;
    }

    /** 이용 완료. 등원 처리(PN-09)가 끝나면 여기로 온다. */
    public void complete() {
        requireStatus(ReservationStatus.CONFIRMED);
        this.status = ReservationStatus.COMPLETED;
    }

    /**
     * 노쇼. 승인됐는데 오지 않은 경우다.
     *
     * <p>⚠️ 이용권은 차감하지 않는다. 차감은 등원 확정에만 대응한다(V8 설계).
     * 노쇼 페널티는 별도 정책이며 아직 정해지지 않았다(UD-B와 함께 결정 필요).
     */
    public void markNoShow() {
        requireStatus(ReservationStatus.CONFIRMED);
        this.status = ReservationStatus.NO_SHOW;
    }

    /**
     * 승인 가능한 상태인지만 확인한다(상태는 바꾸지 않는다).
     *
     * <p><b>왜 confirm()과 따로 열어두는가</b>: 승인 로직은 정원을 세고 나서 confirm()을
     * 부르는데, 정원 검사를 먼저 하면 <b>이미 승인된 예약을 다시 승인</b>할 때 엉뚱한 답이
     * 나간다. 확정된 예약은 스스로 정원을 차지하고 있어서, 정원이 꽉 찬 날이면
     * "정원 초과(RV004)"가 먼저 걸린다 — 점주는 버튼을 두 번 눌렀을 뿐인데 그날이 꽉 찼다는
     * 안내를 받는다.
     *
     * <p>그래서 정원을 세기 전에 이것부터 부른다. 신청(REQUESTED) 상태는 정원을 차지하지
     * 않으므로(ReservationStatus.occupiesCapacity 참조) 순서를 바꿔도 정원 계산은 그대로다.
     */
    public void ensureConfirmable() {
        requireStatus(ReservationStatus.REQUESTED);
    }

    private void requireStatus(ReservationStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATUS);
        }
    }
}
