package com.kkodong.server.domain.enrollment.domain;

import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 등원 신청(KG-06/07 제출 → PN-07 접수·승인). 흐름 F-11 → F-13의 연결 고리다.
 *
 * <p><b>작성은 견주 앱, 처리는 점주 앱.</b> 견주가 신청서를 쓰고 서약서에 동의하면
 * 이 행이 만들어지고(KG-08), 점주 접수함에 뜬다. 점주가 승인하면 <b>그때 원생이 생긴다</b>
 * (FR-PN07-01). 즉 이 행은 "원생이 되기 전 상태"다.
 *
 * <p>⚠️ 어느 버전의 양식·서약서로 낸 신청인지 반드시 고정한다. 양식이 바뀐 뒤 제출값을
 * 보면 어떤 질문에 대한 답인지 알 수 없게 되기 때문이다({@link ApplicationForm} 참조).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "enrollment_applications")
public class EnrollmentApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "dog_id", nullable = false)
    private UUID dogId;

    @Column(name = "applicant_user_id", nullable = false)
    private UUID applicantUserId;

    /**
     * 어느 QR·링크로 들어왔는지(흐름 F-12). 직접 검색해 신청했으면 null.
     * 점주 입장에서 모집 채널별 성과를 보는 유일한 근거다.
     */
    @Column(name = "invite_id")
    private UUID inviteId;

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "consent_document_id", nullable = false)
    private UUID consentDocumentId;

    /** 추가 질문 답변. formId가 가리키는 버전의 extraFields 키에 대응한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_values", nullable = false)
    @Builder.Default
    private Map<String, Object> submittedValues = new HashMap<>();

    /**
     * 항목별 동의 결과. {@code {"accident_liability":true,"photo_public":false}}
     * ⚠️ 사진을 커뮤니티로 내보낼 수 있는지(F-16)를 판단하는 근거가 이 맵이다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consented_items", nullable = false)
    @Builder.Default
    private Map<String, Boolean> consentedItems = new HashMap<>();

    @Column(name = "signature_image_url")
    private String signatureImageUrl;

    @Column(name = "consented_at")
    private OffsetDateTime consentedAt;

    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING; // 컨버터 자동 적용

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 승인(PN-07). ★ 호출부는 이 직후 원생을 만들어야 한다 — 둘은 한 트랜잭션이다.
     *
     * <p>승인만 되고 원생이 안 생기면 견주에게는 "승인됨"으로 보이는데 매장에는 원생이
     * 없어, 등원도 이용권 발급도 되지 않는 상태가 된다.
     */
    public void approve(UUID reviewerUserId) {
        requirePending();
        this.status = ApplicationStatus.APPROVED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = OffsetDateTime.now();
    }

    /** 거절(PN-07, FR-PN07-01). 사유는 견주에게 그대로 전달된다. */
    public void reject(UUID reviewerUserId, String reason) {
        requirePending();
        this.status = ApplicationStatus.REJECTED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = OffsetDateTime.now();
        this.rejectReason = reason;
    }

    /** 특정 항목에 동의했는지. 사진 공개 범위 판단 등에 쓴다(PC-29). */
    public boolean hasConsented(String itemKey) {
        return Boolean.TRUE.equals(consentedItems.get(itemKey));
    }

    private void requirePending() {
        if (this.status != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }
    }
}
