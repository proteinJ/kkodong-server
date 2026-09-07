package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이용권 상품 정의(PN-04, FR-PN04-01).
 *
 * <p><b>상품과 발급분을 분리하는 이유</b>: 상품 가격이나 회차가 바뀌어도 이미 팔린
 * 이용권의 조건은 그대로여야 한다. 그래서 실제 발급분(passes)은 이 행을 참조하되
 * 발급 시점의 값을 스냅샷으로 복사해 간다.
 *
 * <p>판매를 중단해도 행을 지우지 않는다({@code isActive = false}) — 발급된 이용권이
 * 이 행을 참조하고 있고, DB에서도 {@code ON DELETE RESTRICT}로 막혀 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "merchant_products")
public class MerchantProduct {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name; // "주 3회 10회권" 등

    @Column(name = "product_type", nullable = false)
    private ProductType productType; // ProductTypeConverter 자동 적용

    /** 횟수권의 총 회차. 기간권이면 null. */
    @Column(name = "total_count")
    private Integer totalCount;

    /** 발급일로부터의 유효일수. null이면 무기한. */
    @Column(name = "valid_days")
    private Integer validDays;

    /** 원 단위 정수. ⚠️ 통화 계산에 부동소수를 쓰지 않는다. */
    @Column(nullable = false)
    private Integer price;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 이 상품으로 발급할 이용권의 만료일을 계산한다.
     *
     * <p>{@code validDays}가 null이면 무기한이므로 null을 돌려준다 — 호출부에서
     * 날짜 계산이 흩어지지 않게 상품 쪽에 둔다.
     */
    public java.time.LocalDate expiryFrom(java.time.LocalDate issuedOn) {
        return validDays == null ? null : issuedOn.plusDays(validDays);
    }

    /** 판매 중단(PN-04). 행을 지우지 않는 이유는 클래스 주석 참조. */
    public void deactivate() {
        this.isActive = false;
    }
}
