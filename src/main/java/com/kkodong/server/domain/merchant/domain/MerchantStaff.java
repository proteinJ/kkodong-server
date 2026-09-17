package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 매장 소속 스태프(PN-01 역할 분기, PN-03 가입, PN-18 관리).
 *
 * <p><b>점주용 계정 테이블을 따로 만들지 않는다</b>: 미용실 사장도 개를 키운다.
 * 계정은 {@code users} 하나로 두고 "이 사람이 이 매장의 무엇인가"를 이 행으로 얹는다.
 * 한 사람이 견주이면서 원장일 수 있고, 체인점이면 여러 매장의 원장일 수도 있다 —
 * 둘 다 이 구조에서 그냥 된다.
 *
 * <p>권한 검증은 전부 Service 계층 몫이다(PC-12 — Supabase RLS를 쓰지 않는다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "merchant_staff")
public class MerchantStaff {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private StaffRole role; // StaffRoleConverter 자동 적용

    /**
     * 세부 권한. {@code ["attendance", "daily_note", "pass", "enrollment"]} 등.
     *
     * <p>⚠️ {@link StaffRole#DIRECTOR}는 이 값을 보지 않고 전권으로 취급한다 —
     * {@link #can(String)}이 그 규칙을 담고 있으니 호출부에서 리스트를 직접 뒤지지 말 것.
     * <p>⚠️ FR-PN18-01 — 이용권 변경("pass")은 기본적으로 원장 전용이다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private StaffStatus status = StaffStatus.PENDING; // StaffStatusConverter 자동 적용

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "resigned_at")
    private OffsetDateTime resignedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 이 스태프가 해당 권한을 갖는지. 원장은 permissions와 무관하게 항상 참이다.
     *
     * <p>⚠️ 퇴사·승인대기 상태는 권한 이전에 접근 자체가 막혀야 하므로 여기서도 거른다.
     * 호출부가 status 확인을 빠뜨려도 권한이 새지 않게 하려는 것이다.
     */
    public boolean can(String permission) {
        if (status != StaffStatus.ACTIVE) return false;
        if (role == StaffRole.DIRECTOR) return true;
        return permissions.contains(permission);
    }

    /**
     * 권한 부여·변경(PN-18, FR-PN18-01). 전달된 목록으로 통째로 교체한다.
     *
     * <p>부분 추가/삭제가 아니라 교체인 이유: 권한 화면은 체크박스 목록이라 클라이언트가
     * 항상 최종 상태를 보낸다. 델타로 받으면 "해제"를 표현할 방법이 따로 필요해진다.
     *
     * <p>⚠️ 원장에게는 의미가 없다 — {@link #can(String)}이 role을 먼저 보므로
     * 목록이 비어 있어도 전권이다.
     */
    public void grantPermissions(List<String> permissions) {
        this.permissions = new ArrayList<>(permissions);
    }

    /** 원장 승인(PN-18). 승인자를 남겨야 나중에 누가 들였는지 추적할 수 있다. */
    public void approve(UUID approverUserId) {
        this.status = StaffStatus.ACTIVE;
        this.approvedByUserId = approverUserId;
        this.approvedAt = OffsetDateTime.now();
    }

    /**
     * 퇴사 처리(FR-PN18-02). 접근 권한만 즉시 회수하고 과거 작성 이력은 그대로 둔다.
     *
     * <p>⚠️ 마지막 원장의 퇴사는 매장을 잠그므로 Service 계층에서 막아야 한다 —
     * "active director가 1명뿐이면 거부"는 행 단위 제약으로 표현할 수 없다.
     */
    public void resign() {
        this.status = StaffStatus.RESIGNED;
        this.resignedAt = OffsetDateTime.now();
        this.permissions = new ArrayList<>();
    }
}
