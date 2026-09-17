package com.kkodong.server.domain.review.controller;

import com.kkodong.server.domain.review.dto.ReviewRequest;
import com.kkodong.server.domain.review.dto.ReviewResponse;
import com.kkodong.server.domain.review.service.ReviewService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 리뷰 API — PN-20.
 *
 * <p><b>리뷰 작성은 견주 앱 몫이다.</b> 점주 앱은 확인하고 답한다 — 그래서 여기에 리뷰
 * 생성·수정·삭제 엔드포인트가 없다. 점주가 고칠 수 있는 것은 자기 답글뿐이다.
 */
@Tag(name = "Partner-Review",
        description = "[점주] 리뷰 확인과 답글 API (PN-20). 리뷰 작성은 견주 앱 몫이다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 목록·요약 (PN-20)",
            description = """
                    리뷰 목록과 함께 평균 평점·전체 건수·미답변 건수를 반환합니다.

                    ★ unansweredOnly=true 로 부르면 답글 안 단 리뷰만 나옵니다 —
                    점주가 이 화면을 여는 이유가 대체로 그쪽입니다. 배지에는 unansweredCount를 쓰세요.

                    ⚠️ 요약(평균·전체 건수)은 필터와 무관하게 항상 전체 기준입니다.
                    "미답변만 보기"로 걸렀다고 평균 평점이 달라지면 화면이 거짓말을 하게 됩니다.

                    삭제된 리뷰는 목록과 집계 양쪽에서 빠집니다.""")
    @GetMapping
    public ResponseEntity<ApiResponse<ReviewResponse.summary>> getReviews(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "답글 안 단 리뷰만 조회") @RequestParam(defaultValue = "false") boolean unansweredOnly
    ) {
        var response = reviewService.getReviews(merchantId, principal.getUserId(), unansweredOnly);
        return ResponseEntity.ok(ApiResponse.success("리뷰 조회 완료", response));
    }

    @Operation(summary = "리뷰 답글 작성·수정 (PN-20)",
            description = """
                    원장 전용입니다 — 답글은 매장의 공식 입장이기 때문입니다.

                    같은 엔드포인트가 최초 작성과 수정을 겸합니다. 답글은 리뷰당 하나뿐이라
                    작성/수정을 나눌 이유가 없습니다. 수정을 허용하는 이유는 답글이 공개 대화라
                    오타나 부적절한 표현을 바로잡을 수 있어야 하기 때문입니다.

                    ⚠️ 리뷰 본문은 견주가 쓴 것이라 점주가 고칠 수 없습니다.
                    ⚠️ 삭제(신고 처리)된 리뷰에는 답글을 달 수 없습니다(400, RW002) —
                    내려간 리뷰에 답글만 남으면 맥락 없는 글이 됩니다.""")
    @PutMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse.detailInfo>> reply(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "리뷰 ID") @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRequest.reply request
    ) {
        var response = reviewService.reply(merchantId, principal.getUserId(), reviewId, request);
        return ResponseEntity.ok(ApiResponse.success("답글 등록 완료", response));
    }

    @Operation(summary = "리뷰 답글 철회 (PN-20)",
            description = "답글만 지우고 리뷰는 그대로 둡니다. 해당 리뷰는 다시 미답변으로 돌아갑니다.")
    @DeleteMapping("/{reviewId}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse.detailInfo>> removeReply(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "리뷰 ID") @PathVariable UUID reviewId
    ) {
        var response = reviewService.removeReply(merchantId, principal.getUserId(), reviewId);
        return ResponseEntity.ok(ApiResponse.success("답글 철회 완료", response));
    }
}
