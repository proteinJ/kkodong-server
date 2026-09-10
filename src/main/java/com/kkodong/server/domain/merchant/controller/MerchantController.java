package com.kkodong.server.domain.merchant.controller;

import com.kkodong.server.domain.merchant.dto.MerchantRequest;
import com.kkodong.server.domain.merchant.dto.MerchantResponse;
import com.kkodong.server.domain.merchant.service.MerchantService;
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
 * 꼬동 파트너(점주 앱) 매장 API.
 *
 * <p><b>경로를 {@code /api/v1/partner/**}로 나누는 이유</b>: 견주 앱과 점주 앱은 별도 앱이고
 * 계정만 공유한다. 경로를 갈라 두면 "점주 API는 매장 소속·권한을 반드시 검증한다"는 규칙을
 * 경로 단위로 걸 수 있고, Swagger UI에서도 두 앱의 계약이 섞이지 않는다.
 *
 * <p>⚠️ 모든 엔드포인트가 {@code MerchantAccessGuard}를 거친다(PC-12 — RLS가 없다).
 */
@Tag(name = "Partner-Merchant", description = "[점주] 매장 개설·정보·상품·모집 QR 관리 API (PN-02, PN-04, PN-05)")
@RestController
@RequestMapping("/api/v1/partner/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(summary = "매장 개설 (PN-02)",
            description = """
                    사업자등록번호로 매장을 개설합니다. 요청한 계정이 그대로 원장(director)이 됩니다.
                    유치원이면 수용 조건 행도 함께 생성됩니다.
                    이미 등록된 사업자등록번호면 409(P002).""")
    @PostMapping
    public ResponseEntity<ApiResponse<MerchantResponse.detailInfo>> create(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody MerchantRequest.create request
    ) {
        MerchantResponse.detailInfo response = merchantService.create(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("매장 개설 완료", response));
    }

    @Operation(summary = "내가 속한 매장 목록 (PN-01)",
            description = """
                    로그인 직후 점주 앱이 가장 먼저 부르는 API입니다.
                    승인 대기(PENDING) 소속도 함께 내려줍니다 — 선생님이 자기 신청 상태를 알아야 하기 때문입니다.
                    결과가 비어 있으면 매장 개설 또는 소속 신청 화면으로 보내면 됩니다.""")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<MerchantResponse.myMerchantItem>>> getMyMerchants(
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        List<MerchantResponse.myMerchantItem> response = merchantService.getMyMerchants(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("내 매장 목록 조회 완료", response));
    }

    @Operation(summary = "매장 상세 조회 (PN-04)",
            description = "매장 소속 스태프만 조회할 수 있습니다. 소속이 아니면 403(PS001).")
    @GetMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<MerchantResponse.detailInfo>> get(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        MerchantResponse.detailInfo response = merchantService.get(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("매장 조회 완료", response));
    }

    @Operation(summary = "매장 정보 수정 (PN-04)",
            description = """
                    원장 전용입니다. null 필드는 변경하지 않습니다.
                    사업자등록번호·대표자명·업종은 진위확인을 통과한 값이라 이 API로 바꿀 수 없습니다.
                    위도·경도는 반드시 함께 보내야 합니다.""")
    @PatchMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<MerchantResponse.detailInfo>> update(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantRequest.update request
    ) {
        MerchantResponse.detailInfo response = merchantService.update(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("매장 정보 수정 완료", response));
    }

    @Operation(summary = "유치원 수용 조건 조회 (PN-04)",
            description = "수용 체급·일일 정원·중성화/접종 요건. 견주 앱 KG-02 적합도 안내의 근거값입니다.")
    @GetMapping("/{merchantId}/kindergarten-profile")
    public ResponseEntity<ApiResponse<MerchantResponse.kindergartenProfileInfo>> getKindergartenProfile(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = merchantService.getKindergartenProfile(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("수용 조건 조회 완료", response));
    }

    @Operation(summary = "유치원 수용 조건 수정 (PN-04)",
            description = """
                    원장 전용입니다. 일일 정원은 예약 승인 시 정원 검사의 기준값이 됩니다.
                    생략하면 기존 값을 유지합니다.""")
    @PatchMapping("/{merchantId}/kindergarten-profile")
    public ResponseEntity<ApiResponse<MerchantResponse.kindergartenProfileInfo>> updateKindergartenProfile(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantRequest.kindergartenProfile request
    ) {
        var response = merchantService.updateKindergartenProfile(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("수용 조건 수정 완료", response));
    }

    @Operation(summary = "이용권 상품 목록 (PN-04)",
            description = "판매 중단된 상품도 함께 내려옵니다(isActive로 구분). 발급된 이용권이 참조하므로 삭제되지 않습니다.")
    @GetMapping("/{merchantId}/products")
    public ResponseEntity<ApiResponse<List<MerchantResponse.productInfo>>> getProducts(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = merchantService.getProducts(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("상품 목록 조회 완료", response));
    }

    @Operation(summary = "이용권 상품 등록 (PN-04)",
            description = """
                    원장 전용입니다. 횟수권(COUNT)은 totalCount가, 기간권(PERIOD)은 validDays가 필요합니다 —
                    맞지 않으면 400(P005).
                    이 정의가 이후 이용권 발급(PN-12)의 입력이 됩니다.""")
    @PostMapping("/{merchantId}/products")
    public ResponseEntity<ApiResponse<MerchantResponse.productInfo>> createProduct(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantRequest.createProduct request
    ) {
        var response = merchantService.createProduct(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("상품 등록 완료", response));
    }

    @Operation(summary = "이용권 상품 판매 중단 (PN-04)",
            description = "원장 전용입니다. 삭제가 아니라 isActive=false 처리입니다 — 이미 발급된 이용권이 이 상품을 참조합니다.")
    @DeleteMapping("/{merchantId}/products/{productId}")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "상품 ID") @PathVariable UUID productId
    ) {
        merchantService.deactivateProduct(merchantId, principal.getUserId(), productId);
        return ResponseEntity.ok(ApiResponse.success("상품 판매 중단 완료"));
    }

    @Operation(summary = "원생 모집 QR 발급 (PN-05)",
            description = """
                    원장 전용입니다. 매장에 게시할 QR·링크 코드를 발급합니다.
                    견주가 이 코드로 진입하면 곧바로 등원 신청으로 이어집니다(흐름 F-12).
                    validDays를 생략하면 무기한입니다.""")
    @PostMapping("/{merchantId}/invites")
    public ResponseEntity<ApiResponse<MerchantResponse.inviteInfo>> createInvite(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantRequest.createInvite request
    ) {
        var response = merchantService.createInvite(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("모집 QR 발급 완료", response));
    }

    @Operation(summary = "발급한 모집 QR 목록 (PN-05)",
            description = "폐기·만료된 것도 함께 내려옵니다. usable 필드로 현재 사용 가능 여부를 판단하세요.")
    @GetMapping("/{merchantId}/invites")
    public ResponseEntity<ApiResponse<List<MerchantResponse.inviteInfo>>> getInvites(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId
    ) {
        var response = merchantService.getInvites(merchantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("모집 QR 목록 조회 완료", response));
    }

    @Operation(summary = "모집 QR 폐기 (PN-05)",
            description = "원장 전용입니다. 이미 인쇄되어 배포된 QR을 무효화하는 유일한 수단입니다.")
    @DeleteMapping("/{merchantId}/invites/{inviteId}")
    public ResponseEntity<ApiResponse<Void>> revokeInvite(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "초대 ID") @PathVariable UUID inviteId
    ) {
        merchantService.revokeInvite(merchantId, principal.getUserId(), inviteId);
        return ResponseEntity.ok(ApiResponse.success("모집 QR 폐기 완료"));
    }
}
