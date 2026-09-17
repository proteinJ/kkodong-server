package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 원생(PN-10 목록, PN-11 상세). V7__add_enrollment_pass_domain.sql의 enrollments에 매핑.
 *
 * <p><b>지금은 예약이 참조할 최소한만 있다.</b> 신청서 접수(PN-07)·이용권 발급(PN-12) 등
 * 원생 도메인의 나머지는 아직 만들지 않았다 — 예약(PN-19)을 먼저 세우기로 해서다.
 *
 * <p><b>왜 dogId·ownerUserId가 nullable인가</b>: 견주는 언제든 탈퇴할 수 있고
 * (FR-AU07-01, Apple 심사 5.1.1(v) 필수), 탈퇴하면 users → dogs가 CASCADE로 지워진다.
 * 그런데 이 행에는 매장의 이용권 발급·차감 이력과 출석 기록이 매달려 있다 — 매출 데이터다.
 * CASCADE면 견주 한 명의 탈퇴가 매장 회계를 지우고, RESTRICT면 탈퇴 자체가 실패한다.
 * DB에서 ON DELETE SET NULL로 두고 스냅샷을 함께 남기는 것이 둘 다 만족하는 유일한 방법이다.
 *
 * <p>⚠️ 그래서 {@code dogNameSnapshot}은 편의용이 아니라 필수다. 점주 화면이
 * "이름 없는 원생"을 보여주면 안 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** 탈퇴로 null이 될 수 있다 — 클래스 주석 참조. */
    @Column(name = "dog_id")
    private UUID dogId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "dog_name_snapshot", nullable = false)
    private String dogNameSnapshot;

    @Column(name = "dog_breed_snapshot")
    private String dogBreedSnapshot;

    @Column(name = "owner_name_snapshot")
    private String ownerNameSnapshot;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(nullable = false)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE; // EnrollmentStatusConverter 자동 적용

    @Column(name = "enrolled_on", insertable = false, updatable = false)
    private LocalDate enrolledOn; // DB DEFAULT CURRENT_DATE

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    /** ⚠️ PN-11 특이사항 메모. 보호자에게 노출하지 않는다 — 견주 앱 DTO에 싣지 말 것. */
    @Column(name = "staff_memo")
    private String staffMemo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 예약을 받을 수 있는 상태인지. 휴원·퇴원은 예약 대상이 아니다. */
    public boolean isReservable() {
        return status == EnrollmentStatus.ACTIVE;
    }

    /**
     * 특이사항 메모 수정(PN-11).
     *
     * <p>⚠️ 보호자에게 노출하지 않는다(4.데이터항목 비고). 견주 앱 응답 DTO에 이 값을
     * 싣지 말 것 — "사람을 무서워함", "다른 개와 다툰 적 있음" 같은 내용이 들어간다.
     */
    public void updateMemo(String memo) {
        this.staffMemo = memo;
    }

    /**
     * 휴원(PN-11). 이용권은 살아 있지만 예약을 받지 않는다.
     * 장기 여행·질병 등으로 잠시 쉬는 경우이며, 이용권 기간 연장(PN-12)과 함께 쓰인다.
     */
    public void pause() {
        requireStatus(EnrollmentStatus.ACTIVE);
        this.status = EnrollmentStatus.PAUSED;
    }

    /** 복원(PN-11). 휴원에서 재원으로 되돌린다. */
    public void resume() {
        requireStatus(EnrollmentStatus.PAUSED);
        this.status = EnrollmentStatus.ACTIVE;
    }

    /**
     * 퇴원(PN-11). 되돌릴 수 없다 — 다시 다니려면 신청부터 새로 한다.
     *
     * <p>행을 지우지 않는 이유: 이용권 발급·차감 이력과 출석 기록이 매달려 있고
     * 그건 매출 데이터다. 재등록은 새 원생 행으로 처리한다
     * (uq_enrollments_active_dog이 퇴원을 제외하는 이유).
     */
    public void withdraw() {
        if (status == EnrollmentStatus.WITHDRAWN) {
            throw new com.kkodong.server.global.error.BusinessException(
                    com.kkodong.server.global.error.ErrorCode.INVALID_ENROLLMENT_STATUS);
        }
        this.status = EnrollmentStatus.WITHDRAWN;
        this.withdrawnAt = OffsetDateTime.now();
    }

    private void requireStatus(EnrollmentStatus expected) {
        if (this.status != expected) {
            throw new com.kkodong.server.global.error.BusinessException(
                    com.kkodong.server.global.error.ErrorCode.INVALID_ENROLLMENT_STATUS);
        }
    }
}
