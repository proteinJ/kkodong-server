package com.kkodong.server.domain.enrollment.domain;

import com.kkodong.server.domain.merchant.domain.ProductType;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 발급된 이용권(PN-12, KG-13). V7__add_enrollment_pass_domain.sql의 passes에 매핑.
 *
 * <p><b>매출 데이터다.</b> 이 값은 절대 조용히 바뀌어서는 안 된다 — 모든 변동은
 * {@link PassLedger}에 한 줄을 남기고, 잔여 회차는 원장과 항상 일치해야 한다(FR-PN12-01).
 * 그래서 {@code remainingCount}에 setter를 열지 않고 {@link #deduct}/{@link #restore}만 둔다.
 *
 * <p>⚠️ 상태(EXHAUSTED·EXPIRED)를 배치로 갱신하지 않는다. "잔여 0인데 아직 ACTIVE" 같은
 * 시차가 출석 화면에서 곧바로 사고가 되므로, 사용 가능 여부는 {@link #isUsableOn}이
 * 조회 시점에 상태·잔여·만료를 모두 보고 판정한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "passes")
public class Pass {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "product_id")
    private UUID productId;

    /** 발급 시점 상품 조건. 이후 상품 가격·회차가 바뀌어도 팔린 이용권은 그대로여야 한다. */
    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "product_type", nullable = false)
    private ProductType productType; // ProductTypeConverter 자동 적용

    @Column(name = "price_snapshot")
    private Integer priceSnapshot;

    /** 횟수권의 잔여 회차. 기간권이면 null. */
    @Column(name = "remaining_count")
    private Integer remainingCount;

    @Column(name = "total_count")
    private Integer totalCount;

    /** null이면 무기한. FR-PN09-02의 "만료" 판정 기준. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(nullable = false)
    @Builder.Default
    private PassStatus status = PassStatus.ACTIVE; // PassStatusConverter 자동 적용

    @Column(name = "issued_by_user_id")
    private UUID issuedByUserId;

    @Column(name = "issued_at", insertable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 해당 날짜에 쓸 수 있는 이용권인지. FR-PN09-02의 판정이 이것이다.
     *
     * <p>상태·잔여·만료를 한 번에 본다 — 호출부에서 셋을 각각 확인하면 하나를 빠뜨리기 쉽고,
     * 그러면 만료된 이용권으로 등원이 처리돼 매출이 샌다.
     */
    public boolean isUsableOn(LocalDate date) {
        if (status != PassStatus.ACTIVE) return false;
        if (expiresOn != null && expiresOn.isBefore(date)) return false;
        // 기간권은 회차 개념이 없으므로 잔여를 보지 않는다.
        return productType != ProductType.COUNT || (remainingCount != null && remainingCount > 0);
    }

    /**
     * 등원 1회 차감(FR-PN09-03).
     *
     * <p>⚠️ 반드시 등원 확정과 같은 트랜잭션 안에서 부를 것. 하나라도 밖에 있으면
     * "등원은 됐는데 회차가 안 깎인" 행이 남고, 그건 조회로 찾아낼 방법이 없다.
     *
     * @return 차감 후 잔여. 기간권이면 null(차감할 회차가 없다)
     */
    public Integer deduct() {
        if (productType != ProductType.COUNT) return null; // 기간권은 무제한 이용

        if (remainingCount == null || remainingCount <= 0) {
            throw new BusinessException(ErrorCode.PASS_NOT_USABLE);
        }
        this.remainingCount -= 1;
        // 소진되면 즉시 상태를 옮긴다 — 다음 조회가 이 이용권을 후보로 집지 않게 한다.
        if (this.remainingCount == 0) {
            this.status = PassStatus.EXHAUSTED;
        }
        return this.remainingCount;
    }

    /**
     * 되돌리기·취소에 따른 1회 복원(FR-PN09-04).
     *
     * <p>소진되어 EXHAUSTED가 됐던 이용권은 다시 ACTIVE로 돌아온다 — 만료가 아니라
     * 회차 때문에 닫힌 것이므로 회차가 생기면 다시 쓸 수 있어야 한다.
     *
     * @return 복원 후 잔여. 기간권이면 null
     */
    public Integer restore() {
        if (productType != ProductType.COUNT) return null;

        this.remainingCount = (remainingCount == null ? 0 : remainingCount) + 1;
        if (this.status == PassStatus.EXHAUSTED) {
            this.status = PassStatus.ACTIVE;
        }
        return this.remainingCount;
    }
}
