package com.kkodong.server.domain.enrollment.controller;

import com.kkodong.server.domain.enrollment.domain.ApplicationStatus;
import com.kkodong.server.domain.enrollment.dto.ApplicationRequest;
import com.kkodong.server.domain.enrollment.dto.ApplicationResponse;
import com.kkodong.server.domain.enrollment.service.ApplicationFormService;
import com.kkodong.server.domain.enrollment.service.ApplicationInboxService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 신청서 양식·접수함 API — PN-06, PN-07.
 *
 * <p><b>역할 분담</b>: 신청서 작성과 서약서 동의는 견주 앱(KG-06/KG-07)이 하고,
 * 점주 앱은 (1) 무엇을 물어볼지 정의하고(PN-06) (2) 들어온 신청을 처리한다(PN-07).
 * 그래서 여기에 신청서 제출 엔드포인트가 없다.
 *
 * <p>★ 승인이 곧 원생 생성이다(FR-PN07-01). 점주 앱에서 원생이 만들어지는 유일한 경로다.
 */
@Tag(name = "Partner-Application",
        description = "[점주] 신청서 양식·서약서 발행(PN-06)과 신청 접수함·승인·거절(PN-07). 승인 시 원생이 생성된다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationFormService formService;
    private final ApplicationInboxService inboxService;

    // ---------- PN-06 신청서 양식 · 서약서 ----------

    @Operation(summary = "신청서 양식 발행 (PN-06)",
            description = """
                    원장 전용입니다. 견주 앱(KG-06)이 이 정의대로 신청서 화면을 그립니다.

                    ⚠️ 수정이 아니라 새 버전 발행입니다. 기존 활성 양식은 자동으로 내려갑니다 —
                    양식을 고치면 과거 제출값이 어떤 질문에 대한 답인지 알 수 없게 되기 때문입니다
                    ("기타 특이사항"이 3번이었는데 지금은 5번이면 값이 엉뚱한 질문에 붙습니다).

                    extraFields의 key는 버전 안에서 유일해야 합니다.""")
    @PostMapping("/application-forms")
    public ResponseEntity<ApiResponse<ApplicationResponse.formInfo>> publishForm(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody ApplicationRequest.publishForm request
    ) {
        var response = formService.publishForm(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("신청서 양식 발행 완료", response));
    }

    @Operation(summary = "현재 신청서 양식 조회 (PN-06)",
            description = "견주 앱이 받아 갈 것과 같은 내용입니다. 활성 양식이 없으면 404(AP004).")
    @GetMapping("/application-forms/active")
    public ResponseEntity<ApiResponse<ApplicationResponse.formInfo>> getActiveForm(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = formService.getActiveForm(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("신청서 양식 조회 완료", response));
    }

    @Operation(summary = "신청서 양식 버전 이력 (PN-06)",
            description = "내려간 버전도 함께 반환합니다 — 과거 제출값을 해석하려면 당시 양식이 필요합니다.")
    @GetMapping("/application-forms")
    public ResponseEntity<ApiResponse<List<ApplicationResponse.formInfo>>> getFormHistory(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = formService.getFormHistory(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("양식 이력 조회 완료", response));
    }

    @Operation(summary = "서약서 발행 (PN-06)",
            description = """
                    원장 전용입니다. 견주 앱(KG-07)이 항목별로 동의를 받습니다.

                    ⚠️ 사고 책임·촬영 공개·마케팅을 한 덩어리로 묶지 마세요(PC-29).
                    유치원 단체 사진에는 다른 강아지가 함께 찍히는 것이 기본이라, 항목이 분리되어야
                    나중에 그 사진을 커뮤니티로 내보내도 되는지 판단할 수 있습니다.
                    촬영·공개는 required=false로 두는 것을 권합니다 — 필수로 묶으면 사진을 원치 않는
                    보호자가 등원 자체를 못 합니다.

                    마찬가지로 수정이 아니라 새 버전 발행입니다.""")
    @PostMapping("/consent-documents")
    public ResponseEntity<ApiResponse<ApplicationResponse.consentInfo>> publishConsent(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody ApplicationRequest.publishConsent request
    ) {
        var response = formService.publishConsent(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("서약서 발행 완료", response));
    }

    @Operation(summary = "현재 서약서 조회 (PN-06)",
            description = "활성 서약서가 없으면 404(AP005).")
    @GetMapping("/consent-documents/active")
    public ResponseEntity<ApiResponse<ApplicationResponse.consentInfo>> getActiveConsent(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = formService.getActiveConsent(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("서약서 조회 완료", response));
    }

    @Operation(summary = "서약서 버전 이력 (PN-06)",
            description = "FR-KG07-02 — 동의 이력을 재열람하려면 당시 서약서가 필요합니다.")
    @GetMapping("/consent-documents")
    public ResponseEntity<ApiResponse<List<ApplicationResponse.consentInfo>>> getConsentHistory(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = formService.getConsentHistory(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("서약서 이력 조회 완료", response));
    }

    // ---------- PN-07 신청 접수함 ----------

    @Operation(summary = "신청 접수함 (PN-07)",
            description = """
                    견주 앱(KG-06/07)에서 제출된 신청을 최근순으로 반환합니다.
                    status를 생략하면 대기 중(PENDING)만 나옵니다.

                    각 항목에 꼬동 프로필에서 가져온 반려견 정보(견종·체급·체중·중성화)가 함께
                    실려 옵니다 — 수용 조건에 맞는지 바로 판단할 수 있고, 이것이 점주가 꼬동을
                    쓰는 이유입니다(FR-PN11-01). 똑독은 유치원마다 새로 받아야 합니다.""")
    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<List<ApplicationResponse.item>>> getInbox(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "상태 필터. 생략 시 PENDING") @RequestParam(required = false) ApplicationStatus status
    ) {
        var response = inboxService.getInbox(merchantId, principal.getUserId(), status);
        return ResponseEntity.ok(ApiResponse.success("신청 목록 조회 완료", response));
    }

    @Operation(summary = "대기 중 신청 수 (PN-07)",
            description = "홈 배지에 표시할 카운트입니다.")
    @GetMapping("/applications/pending-count")
    public ResponseEntity<ApiResponse<Long>> countPending(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        long count = inboxService.countPending(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("대기 건수 조회 완료", count));
    }

    @Operation(summary = "신청 상세 조회 (PN-07)",
            description = """
                    반려견 정보, 추가 질문 답변, 항목별 동의 결과와 서명을 함께 반환합니다
                    (흐름 F-13 1단계 — 반려견 정보와 서약서를 확인한다).

                    consentedItems로 어떤 항목에 동의했는지 확인하세요 — 특히 촬영·공개 동의는
                    나중에 사진을 다룰 때(PN-15) 근거가 됩니다.""")
    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<ApplicationResponse.item>> get(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "신청 ID") @PathVariable UUID applicationId
    ) {
        var response = inboxService.get(merchantId, principal.getUserId(), applicationId);
        return ResponseEntity.ok(ApiResponse.success("신청 상세 조회 완료", response));
    }

    @Operation(summary = "신청 승인 (PN-07) ★ 원생 생성 지점",
            description = """
                    신청을 승인하고 그 자리에서 원생(enrollment)을 만듭니다.
                    응답의 enrollmentId로 곧바로 이용권 발급(PN-12)으로 이어가면 됩니다(흐름 F-13 3단계).

                    ⚠️ 승인과 원생 생성은 하나의 트랜잭션입니다. 승인만 되고 원생이 안 생기면
                    견주에게는 "승인됨"으로 보이는데 매장에는 원생이 없어 등원도 이용권 발급도
                    되지 않고, 신청은 이미 처리됨이라 다시 승인할 수도 없습니다.

                    원생에는 이름·견종이 스냅샷으로 복사됩니다 — 견주가 나중에 탈퇴해도
                    점주 화면에 이름이 남아야 하기 때문입니다.

                    실패: 이미 처리된 신청 400(AP002) · 이미 재원 중 409(AP003) · 반려견 없음 404(D003)""")
    @PostMapping("/applications/{applicationId}/approve")
    public ResponseEntity<ApiResponse<ApplicationResponse.approveResult>> approve(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "신청 ID") @PathVariable UUID applicationId
    ) {
        var response = inboxService.approve(merchantId, principal.getUserId(), applicationId);
        return ResponseEntity.ok(ApiResponse.success("신청 승인 완료", response));
    }

    @Operation(summary = "신청 거절 (PN-07)",
            description = """
                    사유가 필수이며 견주에게 그대로 전달됩니다. 원생은 만들어지지 않습니다.
                    대기 중(PENDING) 신청만 거절할 수 있습니다(400, AP002).""")
    @PostMapping("/applications/{applicationId}/reject")
    public ResponseEntity<ApiResponse<ApplicationResponse.item>> reject(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "신청 ID") @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationRequest.reject request
    ) {
        var response = inboxService.reject(merchantId, principal.getUserId(), applicationId, request);
        return ResponseEntity.ok(ApiResponse.success("신청 거절 완료", response));
    }
}
