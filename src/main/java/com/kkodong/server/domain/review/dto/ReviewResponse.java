package com.kkodong.server.domain.review.dto;

import com.kkodong.server.domain.review.domain.MerchantReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ReviewResponse {

    /** 리뷰 한 건과 점주 답글(PN-20, KG-02). */
    public record detailInfo(
            @Schema(description = "리뷰 ID") UUID id,
            @Schema(description = "작성자 이름. 탈퇴 후에도 남는 스냅샷") String authorName,
            @Schema(description = "평점 1~5") Short rating,
            @Schema(description = "리뷰 본문") String content,
            @Schema(description = "이용 근거가 된 예약 ID") UUID reservationId,
            @Schema(description = "작성 시각") OffsetDateTime createdAt,

            @Schema(description = "점주 답글. null이면 아직 답하지 않음") String replyBody,
            @Schema(description = "답글 시각") OffsetDateTime repliedAt,
            @Schema(description = "답글이 필요한 리뷰인지 — 점주가 실제로 찾는 것은 이쪽이다")
            Boolean unanswered
    ) {
        public static detailInfo from(MerchantReview r) {
            return new detailInfo(
                    r.getId(), r.getAuthorNameSnapshot(), r.getRating(), r.getContent(),
                    r.getReservationId(), r.getCreatedAt(),
                    r.getReplyBody(), r.getRepliedAt(), r.isUnanswered());
        }
    }

    /**
     * 리뷰 요약(PN-20 상단, KG-02 매장 상세).
     *
     * <p>{@code unansweredCount}가 이 화면의 배지가 된다 — 평균 평점은 견주에게 보여줄 값이고,
     * 점주에게 필요한 것은 "내가 답해야 할 게 몇 개인가"다.
     */
    public record summary(
            @Schema(description = "평균 평점. 리뷰가 없으면 0") Double averageRating,
            @Schema(description = "리뷰 수(삭제 제외)") long totalCount,
            @Schema(description = "답글 안 단 리뷰 수") long unansweredCount,
            @Schema(description = "리뷰 목록") List<detailInfo> reviews
    ) {}
}
