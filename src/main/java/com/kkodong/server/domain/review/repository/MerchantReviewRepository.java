package com.kkodong.server.domain.review.repository;

import com.kkodong.server.domain.review.domain.MerchantReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MerchantReviewRepository extends JpaRepository<MerchantReview, UUID> {

    /** PN-20 리뷰 목록 · KG-02 매장 상세. 삭제된 리뷰는 제외한다. */
    List<MerchantReview> findAllByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID merchantId);

    /**
     * 답글 안 단 리뷰(PN-20).
     *
     * <p>점주가 이 화면을 여는 이유가 이것이다 — 전체 목록보다 "내가 답해야 할 것"이 먼저다.
     * DB의 idx_merchant_reviews_unanswered가 이 질의를 받는다.
     */
    List<MerchantReview> findAllByMerchantIdAndReplyBodyIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID merchantId);

    /**
     * 평점 요약(PN-20 상단, KG-02 매장 상세). 평균과 건수를 한 번에 읽는다.
     *
     * <p>⚠️ Object[]로 받지 않는다 — 배열의 모양을 컴파일러가 검사해 주지 않아
     * 캐스팅을 잘못해도 실행 시에야 터진다(실제로 그렇게 한 번 깨졌다).
     * 별칭과 getter 이름이 대응하는 프로젝션으로 받는다.
     */
    @Query("""
            select coalesce(avg(r.rating), 0) as averageRating,
                   count(r)                   as totalCount
            from MerchantReview r
            where r.merchantId = :merchantId and r.deletedAt is null
            """)
    ReviewSummaryRow summarize(@Param("merchantId") UUID merchantId);
}
