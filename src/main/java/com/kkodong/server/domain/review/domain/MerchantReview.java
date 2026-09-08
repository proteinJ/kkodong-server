package com.kkodong.server.domain.review.domain;

import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 견주 리뷰와 점주 답글(PN-20 관리, KG-02 열람).
 *
 * <p><b>작성은 견주 앱 몫이고, 점주 앱은 확인과 답글만 맡는다.</b> 그래서 이 도메인에
 * 리뷰 생성 경로가 없다 — 신청서(PN-06/07)와 같은 분담이다.
 *
 * <p><b>실제 이용자만 쓸 수 있다</b>: {@code reservationId}를 걸어 "이용한 적 있는 사람의
 * 리뷰"임을 스키마로 보장한다. 이게 없으면 경쟁 매장이나 무관한 사람이 평점을 흔들 수 있고,
 * 그러면 KG-02의 리뷰가 견주에게 아무 신호도 못 준다.
 *
 * <p>답글을 별도 테이블로 빼지 않은 이유: 리뷰 하나에 답글 하나이고 스레드가 아니다.
 * 1:1을 두 테이블로 나누면 조회마다 조인만 늘어난다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "merchant_reviews")
public class MerchantReview {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** 이용 근거. 예약이 지워져도 리뷰는 남아야 하므로 nullable이지만, 작성 시점에는 필수다. */
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    /** 탈퇴해도 리뷰는 남는다 — 그때도 "누가 썼는지 모를" 리뷰가 되면 안 된다. */
    @Column(name = "author_name_snapshot", nullable = false)
    private String authorNameSnapshot;

    @Column(nullable = false)
    private Short rating;

    private String content;

    @Column(name = "reply_body")
    private String replyBody;

    @Column(name = "replied_by_user_id")
    private UUID repliedByUserId;

    @Column(name = "replied_at")
    private OffsetDateTime repliedAt;

    /** SAFETY 도메인과 같은 soft delete. 신고 처리 이력을 보존해야 한다. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 답글 작성·수정(PN-20).
     *
     * <p>수정을 허용하는 이유: 답글은 공개 대화라 오타나 부적절한 표현을 바로잡을 수 있어야
     * 한다. 리뷰 본문(견주가 쓴 것)은 점주가 건드릴 수 없다 — 그건 여기 메서드가 없는 이유다.
     *
     * <p>⚠️ 삭제된 리뷰에는 답글을 달지 않는다. 신고로 내려간 리뷰에 답글이 붙으면
     * 그 답글만 남아 맥락 없는 글이 된다.
     */
    public void reply(String body, UUID repliedByUserId) {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.REVIEW_DELETED);
        }
        this.replyBody = body;
        this.repliedByUserId = repliedByUserId;
        this.repliedAt = OffsetDateTime.now();
    }

    /** 답글 철회(PN-20). 답글만 지우고 리뷰는 그대로 둔다. */
    public void removeReply() {
        this.replyBody = null;
        this.repliedByUserId = null;
        this.repliedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 점주가 아직 답하지 않은 리뷰인지. PN-20에서 점주가 실제로 찾는 것이 이쪽이다. */
    public boolean isUnanswered() {
        return replyBody == null && !isDeleted();
    }
}
