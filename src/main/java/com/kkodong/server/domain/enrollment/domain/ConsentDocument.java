package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 서약서(PN-06 등록, KG-07 견주 동의). 점주가 등록하고 견주 앱이 항목별로 동의받는다.
 *
 * <p><b>왜 항목을 분리해 저장하는가</b> (PC-29): 유치원 단체 사진에는 다른 강아지가 함께
 * 찍히는 것이 기본이다. "사고 책임"에 동의한 것과 "촬영·공개"에 동의한 것을 한 덩어리로
 * 받으면, 나중에 이 사진을 커뮤니티로 내보내도 되는지(F-16) 판단할 근거가 없다.
 * 동의는 항목별로 받고 항목별로 기록해야 그 판단이 가능하다.
 *
 * <p>양식과 같은 이유로 버전을 남긴다 — 서약서를 고친 뒤 과거 동의 이력을 보면
 * 무엇에 동의한 것인지 알 수 없게 된다(FR-KG07-02: 양쪽이 재열람할 수 있어야 한다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "consent_documents")
public class ConsentDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String body; // 서약서 전문

    /** 개별 동의 항목. 항목 key 세트(사고책임·촬영공개·마케팅)의 유효성은 서버가 본다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<ConsentItem> items = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 동의 항목 한 개.
     *
     * @param key      동의 이력 맵의 키. photo_public 등
     * @param label    견주에게 보이는 문구
     * @param required 필수 동의 여부. ⚠️ 촬영·공개는 선택이어야 한다 —
     *                 필수로 묶으면 사진을 원치 않는 보호자가 등원 자체를 못 한다
     */
    public record ConsentItem(String key, String label, Boolean required) {}
}
