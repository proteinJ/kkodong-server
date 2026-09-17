package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * V6__add_merchant_domain.sql의 merchants 테이블에 매핑. 점주 앱(PN-02 개설, PN-04 관리)의
 * 루트이자, 견주 앱 KG-01/KG-02가 지도에서 찾는 대상이다.
 *
 * <p><b>왜 kindergartens가 아니라 merchants인가</b>: 예약은 유치원에서 시작하지만
 * 미용실·병원으로 확장한다. 매장·스태프·권한·예약 상태머신은 세 업종이 같고,
 * 이용권 회차·등원/하원·일일 정원은 유치원에만 있다. 공통분모만 여기 두고 업종 고유값은
 * {@link KindergartenProfile} 같은 형제 테이블로 내린다(V6 1번 주석 참조).
 *
 * <p>FK는 관계 매핑(@ManyToOne) 없이 UUID 값으로 둔다 — {@link com.kkodong.server.domain.dog.domain.Dog}과 같은 방식.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_type", nullable = false)
    @Builder.Default
    private MerchantType merchantType = MerchantType.KINDERGARTEN; // MerchantTypeConverter 자동 적용

    @Column(nullable = false)
    private String name; // 상호

    /**
     * ⚠️ 국세청 진위확인(FR-PN02-01)을 통과한 값만 저장한다. 하이픈 없는 10자리로
     * 정규화할 것 — 표기가 흔들리면 UNIQUE가 무력해져 같은 매장이 둘로 생긴다.
     */
    @Column(name = "business_registration_number", nullable = false)
    private String businessRegistrationNumber;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Column(name = "business_opened_on")
    private LocalDate businessOpenedOn;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt; // null이면 진위확인 미완료

    private String address;

    // PostGIS geography(Point,4326). ⚠️ 좌표 순서가 (경도 lng, 위도 lat)로 직관과 반대다 —
    // 생성은 반드시 Locations.of(lat, lng)를 거칠 것(User.homeLocation과 같은 규칙).
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    private String phone;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", nullable = false)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", nullable = false)
    @Builder.Default
    private List<BusinessHour> businessHours = new ArrayList<>();

    /** 임시 휴무일. ISO 날짜 문자열("2026-09-30")로 담는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "closed_dates", nullable = false)
    @Builder.Default
    private List<String> closedDates = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING; // MerchantStatusConverter 자동 적용

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // DB DEFAULT now()가 관리

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 사업자 진위확인 통과 처리. 이 시점에 매장이 견주 앱에 노출되기 시작한다.
     *
     * <p>PN-02 가입 흐름(F-17)에서 검증 성공 직후 호출된다. 검증에 실패하면
     * 가입 자체를 진행하지 않으므로(FR-PN02-01) 실패 경로는 이 엔티티에 없다.
     */
    public void verify() {
        if (this.verifiedAt == null) {
            this.verifiedAt = OffsetDateTime.now();
            this.status = MerchantStatus.ACTIVE;
        }
    }

    /** PATCH용 부분 수정(PN-04) — null 필드는 기존 값을 그대로 둔다. */
    public void patch(MerchantUpdate command) {
        if (command.name() != null) this.name = command.name();
        if (command.address() != null) this.address = command.address();
        if (command.location() != null) this.location = command.location();
        if (command.phone() != null) this.phone = command.phone();
        if (command.description() != null) this.description = command.description();
        if (command.imageUrls() != null) this.imageUrls = command.imageUrls();
        if (command.businessHours() != null) this.businessHours = command.businessHours();
        if (command.closedDates() != null) this.closedDates = command.closedDates();
    }
}
