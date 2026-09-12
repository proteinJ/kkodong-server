package com.kkodong.server.domain.review.service;

import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.review.domain.MerchantReview;
import com.kkodong.server.domain.review.dto.ReviewRequest;
import com.kkodong.server.domain.review.dto.ReviewResponse;
import com.kkodong.server.domain.review.repository.MerchantReviewRepository;
import com.kkodong.server.domain.review.repository.ReviewSummaryRow;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * 리뷰 확인과 답글(PN-20).
 *
 * <p><b>리뷰 작성은 견주 앱 몫이다.</b> 점주 앱은 확인하고 답한다 — 그래서 이 서비스에
 * 리뷰 생성·수정·삭제가 없다. 점주가 고칠 수 있는 것은 자기 답글뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final MerchantReviewRepository reviewRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * 리뷰 목록과 요약(PN-20).
     *
     * @param unansweredOnly true면 답글 안 단 리뷰만. 점주가 이 화면을 여는 이유가 그쪽이다
     */
    public ReviewResponse.summary getReviews(
            UUID merchantId, UUID userId, boolean unansweredOnly) {
        accessGuard.requireStaff(merchantId, userId);

        List<MerchantReview> reviews = unansweredOnly
                ? reviewRepository
                    .findAllByMerchantIdAndReplyBodyIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(merchantId)
                : reviewRepository.findAllByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(merchantId);

        // 요약은 필터와 무관하게 전체 기준이어야 한다 — "미답변만 보기"로 걸렀다고
        // 평균 평점이 달라지면 화면이 거짓말을 한다.
        ReviewSummaryRow row = reviewRepository.summarize(merchantId);
        double average = row.getAverageRating() == null ? 0 : row.getAverageRating();
        long total = row.getTotalCount();

        long unanswered = unansweredOnly ? reviews.size()
                : reviews.stream().filter(MerchantReview::isUnanswered).count();

        return new ReviewResponse.summary(
                BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                total,
                unanswered,
                reviews.stream().map(ReviewResponse.detailInfo::from).toList());
    }

    /**
     * 답글 작성·수정(PN-20).
     *
     * <p>같은 엔드포인트가 최초 작성과 수정을 겸한다 — 답글은 하나뿐이라 "이미 답했으면
     * 수정 API를 쓰라"고 나눌 이유가 없다.
     */
    @Transactional
    public ReviewResponse.detailInfo reply(
            UUID merchantId, UUID userId, UUID reviewId, ReviewRequest.reply request) {
        // 답글은 매장의 공식 입장이라 원장 전용으로 둔다. 선생님이 대신 써야 한다면
        // 별도 권한을 만들 것 — 지금은 그런 요구가 없다.
        accessGuard.requireDirector(merchantId, userId);

        MerchantReview review = findInMerchant(merchantId, reviewId);
        review.reply(request.body(), userId);
        return ReviewResponse.detailInfo.from(review);
    }

    /** 답글 철회(PN-20). 답글만 지우고 리뷰는 그대로 둔다. */
    @Transactional
    public ReviewResponse.detailInfo removeReply(UUID merchantId, UUID userId, UUID reviewId) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantReview review = findInMerchant(merchantId, reviewId);
        review.removeReply();
        return ReviewResponse.detailInfo.from(review);
    }

    /** ⚠️ 남의 매장 리뷰 ID를 넣어도 통과하지 않게 소속을 대조한다. */
    private MerchantReview findInMerchant(UUID merchantId, UUID reviewId) {
        MerchantReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        return review;
    }
}
