package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 원생 모집 QR / 링크(PN-05, FR-PN05-01). 매장에 게시한 QR로 견주가 즉시 연결된다 —
 * 흐름 F-12의 콜드스타트 경로다.
 *
 * <p>⚠️ {@code code}에 {@code merchantId}를 그대로 싣지 않는다. 코드를 폐기해도 매장
 * 식별자는 바뀌지 않으므로, id를 실으면 한 번 유출된 링크를 영원히 막을 수 없다.
 * 추측 불가능한 난수를 쓸 것.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "merchant_invites")
public class MerchantInvite {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** QR·딥링크에 실리는 공개 코드. URL-safe 난수. */
    @Column(nullable = false)
    private String code;

    /** null이면 무기한. */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 지금 이 코드로 신청까지 진행할 수 있는지.
     *
     * <p>FR-PN05-01의 "폐기·만료 QR은 안내 후 진행을 막는다"가 이 판정이다.
     * 만료와 폐기를 호출부에서 각각 확인하면 한쪽을 빠뜨리기 쉬워 여기 모아둔다.
     */
    public boolean isUsableAt(OffsetDateTime now) {
        if (revokedAt != null) return false;
        return expiresAt == null || expiresAt.isAfter(now);
    }

    /** 폐기(PN-05). 이미 인쇄돼 나간 QR을 무효화하는 유일한 수단이다. */
    public void revoke() {
        if (this.revokedAt == null) this.revokedAt = OffsetDateTime.now();
    }
}
