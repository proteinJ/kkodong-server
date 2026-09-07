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
 * 유치원 고유의 수용 조건(PN-04, FR-PN04-02). 견주 앱 KG-02의 "적합도 문장"이
 * 이 값들을 근거로 만들어진다.
 *
 * <p>{@link Merchant}에 합치지 않는 이유: 미용실에는 정원도 접종요건도 없다.
 * 업종이 늘 때마다 merchants에 NULL 컬럼이 쌓이는 걸 막으려고 형제 테이블로 뺐다.
 *
 * <p>매장당 하나이므로 PK를 FK와 겸하게 해서 1:1을 스키마로 강제한다 —
 * {@code @GeneratedValue}가 없는 것은 그래서다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "kindergarten_profiles")
public class KindergartenProfile {

    @Id
    @Column(name = "merchant_id")
    private UUID merchantId; // merchants.id FK 겸 PK

    /** 수용 가능 체급. {@code dogs.size}와 같은 값 세트(small/medium/large). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accepted_sizes", nullable = false)
    @Builder.Default
    private List<String> acceptedSizes = new ArrayList<>();

    /**
     * 일일 정원. 예약 승인 시 정원 검사의 기준값이다(V8).
     *
     * <p>⚠️ null은 "무제한"이다. 0("아무도 못 받음")과 구분해야 하므로 기본값을 두지 않는다.
     * <p>⚠️ 이 상한은 DB 제약으로 표현할 수 없다 — 승인 트랜잭션에서 (매장, 날짜) 단위
     * advisory lock을 잡고 직접 세야 한다(V8 reservations 주석 참조).
     */
    @Column(name = "daily_capacity")
    private Integer dailyCapacity;

    @Column(name = "requires_neutered", nullable = false)
    @Builder.Default
    private Boolean requiresNeutered = false;

    /** 요구 접종 항목. 값 세트는 서버 enum이 맡는다(personality_traits와 같은 분담). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_vaccinations", nullable = false)
    @Builder.Default
    private List<String> requiredVaccinations = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 정원 미설정(무제한)인지. 호출부에서 null 비교가 흩어지는 걸 막는다. */
    public boolean hasUnlimitedCapacity() {
        return dailyCapacity == null;
    }
}
