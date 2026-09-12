package com.kkodong.server.domain.enrollment.controller;

import com.kkodong.server.domain.enrollment.domain.EnrollmentSort;
import com.kkodong.server.domain.enrollment.domain.EnrollmentStatus;
import com.kkodong.server.domain.enrollment.dto.EnrollmentRequest;
import com.kkodong.server.domain.enrollment.dto.EnrollmentResponse;
import com.kkodong.server.domain.enrollment.service.EnrollmentQueryService;
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

import java.util.List;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 원생 API — PN-10 목록, PN-11 상세.
 *
 * <p>원생은 신청 승인(PN-07)으로만 생긴다. 여기서는 조회하고 관리한다.
 */
@Tag(name = "Partner-Enrollment",
        description = "[점주] 원생 목록·상세·메모·상태 관리 API (PN-10, PN-11)")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentQueryService enrollmentQueryService;

    @Operation(summary = "원생 목록 (PN-10)",
            description = """
                    이용권 잔여·만료와 등원 집계를 함께 담아 반환합니다. 목록에서 바로 판단할 수
                    있도록 서버가 계산해 내려줍니다.

                    ★ sort=PAYMENT_DUE 가 사실상 영업 리스트입니다(FR-PN10-01).
                    재결제 안내가 급한 순서로 정렬됩니다 — 대상 먼저, 잔여가 적은 순, 만료가 가까운 순.

                    paymentDue는 잔여 2회 미만 또는 만료 7일 이내면 true입니다(FR-PN17-01과 같은 기준).
                    ⚠️ 이용권이 아예 없는 원생도 true입니다 — 등원 처리 자체가 막히므로 가장 급합니다.

                    status를 생략하면 재원 중(ACTIVE)만 나옵니다. 퇴원생까지 섞이면 목록이 매년 불어납니다.
                    keyword는 강아지 이름과 보호자 이름 양쪽에서 부분 일치로 찾습니다.

                    remainingCount가 null이면 기간권만 보유한 것입니다 — 0(다 씀)과 다릅니다.""")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EnrollmentResponse.listItem>>> getList(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "재원 상태 필터. 생략 시 ACTIVE")
            @RequestParam(required = false) EnrollmentStatus status,
            @Parameter(description = "정렬. 생략 시 ENROLLED_DESC")
            @RequestParam(required = false) EnrollmentSort sort,
            @Parameter(description = "검색어(강아지 이름 또는 보호자 이름)")
            @RequestParam(required = false) String keyword
    ) {
        var response = enrollmentQueryService.getList(
                merchantId, principal.getUserId(), status, sort, keyword);
        return ResponseEntity.ok(ApiResponse.success("원생 목록 조회 완료", response));
    }

    @Operation(summary = "원생 상세 (PN-11)",
            description = """
                    반려견 정보·보호자·이용권·최근 등원 이력·특이사항 메모를 한 번에 반환합니다.

                    ★ dog 필드는 꼬동 프로필에서 그대로 가져온 것입니다(FR-PN11-01) —
                    견종·생일·체급·체중·중성화·성향 태그를 유치원이 새로 받지 않아도 됩니다.
                    점주가 꼬동을 쓰는 이유가 이 필드입니다.

                    ⚠️ 견주가 탈퇴하면 dog는 null이 됩니다. 그때도 화면이 비지 않도록
                    dogNameSnapshot/ownerNameSnapshot을 함께 내려줍니다.

                    ⚠️ staffMemo는 보호자 비공개입니다. 견주 앱에 전달하지 마세요.

                    등원 이력은 최근 20건입니다.""")
    @GetMapping("/{enrollmentId}")
    public ResponseEntity<ApiResponse<EnrollmentResponse.detailInfo>> getDetail(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId
    ) {
        var response = enrollmentQueryService.getDetail(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("원생 상세 조회 완료", response));
    }

    @Operation(summary = "특이사항 메모 수정 (PN-11)",
            description = """
                    ⚠️ 보호자에게 노출되지 않는 내부 메모입니다("낯선 사람을 무서워함" 등).

                    null 또는 빈 문자열을 보내면 메모가 지워집니다 — 다른 PATCH와 달리
                    "안 바꿈"이 아닙니다(필드가 하나뿐이라 그 해석이 더 자연스럽습니다).""")
    @PatchMapping("/{enrollmentId}/memo")
    public ResponseEntity<ApiResponse<EnrollmentResponse.detailInfo>> updateMemo(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId,
            @Valid @RequestBody EnrollmentRequest.updateMemo request
    ) {
        var response = enrollmentQueryService.updateMemo(
                merchantId, principal.getUserId(), enrollmentId, request);
        return ResponseEntity.ok(ApiResponse.success("메모 수정 완료", response));
    }

    @Operation(summary = "휴원 처리 (PN-11)",
            description = """
                    이용권은 살아 있지만 예약을 받지 않습니다. 장기 여행·질병 등으로 잠시 쉴 때 씁니다.
                    휴원 기간만큼 이용권을 늘려주려면 기간 연장(PN-12)을 함께 호출하세요.

                    재원 중(ACTIVE)인 원생만 휴원할 수 있습니다(400, EN003).""")
    @PostMapping("/{enrollmentId}/pause")
    public ResponseEntity<ApiResponse<EnrollmentResponse.detailInfo>> pause(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId
    ) {
        var response = enrollmentQueryService.pause(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("휴원 처리 완료", response));
    }

    @Operation(summary = "휴원 해제 (PN-11)",
            description = "휴원 중(PAUSED)인 원생을 재원으로 되돌립니다(400, EN003).")
    @PostMapping("/{enrollmentId}/resume")
    public ResponseEntity<ApiResponse<EnrollmentResponse.detailInfo>> resume(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId
    ) {
        var response = enrollmentQueryService.resume(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("휴원 해제 완료", response));
    }

    @Operation(summary = "퇴원 처리 (PN-11)",
            description = """
                    ⚠️ 되돌릴 수 없습니다. 다시 다니려면 신청(KG-06)부터 새로 해야 하며,
                    그때는 새 원생으로 등록됩니다.

                    원생 행은 지우지 않습니다 — 이용권 발급·차감 이력과 출석 기록이 매달려 있고
                    그건 매출 데이터입니다.

                    ⚠️ 남은 이용권은 자동 환불되지 않습니다. 정산은 점주 판단이므로
                    필요하면 이용권 환불(PN-12)을 별도로 호출하세요 — 되돌릴 수 없는 매출 처리가
                    퇴원 버튼 하나에 딸려 나가지 않게 한 것입니다.""")
    @PostMapping("/{enrollmentId}/withdraw")
    public ResponseEntity<ApiResponse<EnrollmentResponse.detailInfo>> withdraw(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @PathVariable UUID enrollmentId
    ) {
        var response = enrollmentQueryService.withdraw(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("퇴원 처리 완료", response));
    }
}
