package com.kkodong.server.domain.assignment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 담당 배정(PN-13, FR-PN13-01). 오늘 어느 선생님이 어느 강아지를 맡는지.
 *
 * <p>담당 마릿수는 이 테이블의 group by 로 나온다 — 선생님 한 명에게 몰리지 않게 하는 것이
 * 이 화면의 목적이므로, 카운트가 배정만큼이나 중요한 출력이다.
 *
 * <p>⚠️ {@code staffId}는 {@code users}가 아니라 <b>{@code merchant_staff}</b>를 참조한다.
 * "이 매장의 스태프로서" 담당하는 것이기 때문이다 — 퇴사하면 과거 배정 이력은 남되,
 * 그 사람이 여전히 매장 소속인지는 merchant_staff가 답한다.
 *
 * <p>한 강아지는 하루에 한 선생님이 맡는다(DB의 uq_staff_assignments). 둘로 나누면
 * 사고가 났을 때 누구 책임인지가 흐려진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "staff_assignments")
public class StaffAssignment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    /** merchant_staff.id — users.id가 아니다. 클래스 주석 참조. */
    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 담당 선생님 교체. 배정을 지웠다 만들지 않고 옮긴다 — 배정 시각이 보존된다. */
    public void reassignTo(UUID staffId) {
        this.staffId = staffId;
    }
}
